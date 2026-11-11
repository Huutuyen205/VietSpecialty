package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.CartItem;
import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.ProductImage;
import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.response.CartItemResponse;
import com.vietspecialty.dto.response.CartSummaryResponse;
import com.vietspecialty.dto.response.ShopCartGroupResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.CartItemRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.CartService;
import com.vietspecialty.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    public CartSummaryResponse getCart(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return buildCartSummary(cartItems);
    }

    @Override
    @Transactional
    public CartSummaryResponse addToCart(Long userId, CartItemRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK, "Sản phẩm hiện đang tạm ngừng kinh doanh");
        }

        // Chặn người bán tự mua hàng của chính gian hàng mình
        if (product.getShop() != null && product.getShop().getOwner() != null
                && product.getShop().getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_BUY_OWN_PRODUCT);
        }

        if (product.getStock() <= 0) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByUserIdAndProductId(userId, product.getId());

        if (existingItemOpt.isPresent()) {
            CartItem item = existingItemOpt.get();
            int newQuantity = item.getQuantity() + request.quantity();
            if (newQuantity > product.getStock()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                        "Số lượng trong kho chỉ còn " + product.getStock() + " " + product.getUnit());
            }
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            if (request.quantity() > product.getStock()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                        "Số lượng trong kho chỉ còn " + product.getStock() + " " + product.getUnit());
            }
            CartItem newItem = CartItem.builder()
                    .user(user)
                    .product(product)
                    .quantity(request.quantity())
                    .build();
            cartItemRepository.save(newItem);
        }

        log.info("User {} added product {} (qty: {}) to cart", userId, product.getId(), request.quantity());
        return getCart(userId);
    }

    @Override
    @Transactional
    public CartSummaryResponse updateQuantity(Long userId, Long cartItemId, Integer quantity) {
        CartItem item = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (quantity == null || quantity <= 0) {
            cartItemRepository.delete(item);
            log.info("Removed cartItem {} for user {} because quantity <= 0", cartItemId, userId);
            return getCart(userId);
        }

        Product product = item.getProduct();
        if (quantity > product.getStock()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                    "Số lượng trong kho chỉ còn " + product.getStock() + " " + product.getUnit());
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return getCart(userId);
    }

    @Override
    @Transactional
    public CartSummaryResponse removeFromCart(Long userId, Long cartItemId) {
        CartItem item = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        cartItemRepository.delete(item);
        log.info("User {} removed cartItem {}", userId, cartItemId);
        return getCart(userId);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
        log.info("Cleared entire cart for user {}", userId);
    }

    @Override
    public int getCartItemCount(Long userId) {
        if (userId == null) return 0;
        return (int) cartItemRepository.countByUserId(userId);
    }

    // ==========================================
    // HELPER: GOM NHÓM THEO GIAN HÀNG & TÍNH TIỀN
    // ==========================================
    private CartSummaryResponse buildCartSummary(List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return new CartSummaryResponse(Collections.emptyList(), 0, BigDecimal.ZERO, "0₫");
        }

        // Nhóm cart items theo shopId (giữ thứ tự chèn)
        Map<Long, List<CartItem>> itemsByShop = new LinkedHashMap<>();
        for (CartItem ci : cartItems) {
            Long shopId = (ci.getProduct() != null && ci.getProduct().getShop() != null)
                    ? ci.getProduct().getShop().getId()
                    : 0L;
            itemsByShop.computeIfAbsent(shopId, k -> new ArrayList<>()).add(ci);
        }

        List<ShopCartGroupResponse> shopGroups = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        int totalItemCount = 0;

        for (Map.Entry<Long, List<CartItem>> entry : itemsByShop.entrySet()) {
            List<CartItem> groupItems = entry.getValue();
            if (groupItems.isEmpty()) continue;

            Shop shop = groupItems.get(0).getProduct().getShop();
            String shopName = shop != null ? shop.getName() : "Gian hàng đặc sản";
            String shopSlug = shop != null ? shop.getSlug() : "";
            String provinceName = (shop != null && shop.getProvince() != null) ? shop.getProvince().getName() : "";

            List<CartItemResponse> itemResponses = new ArrayList<>();
            BigDecimal groupTotal = BigDecimal.ZERO;

            for (CartItem ci : groupItems) {
                Product p = ci.getProduct();
                BigDecimal price = p.getPrice();
                int qty = ci.getQuantity();
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));

                String primaryImg = null;
                if (p.getImages() != null && !p.getImages().isEmpty()) {
                    primaryImg = p.getImages().stream()
                            .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                            .findFirst()
                            .map(ProductImage::getImageUrl)
                            .orElse(p.getImages().get(0).getImageUrl());
                }

                boolean isOutOfStock = p.getStock() <= 0 || p.getStatus() != ProductStatus.ACTIVE;

                CartItemResponse cir = new CartItemResponse(
                        ci.getId(),
                        p.getId(),
                        p.getName(),
                        p.getSlug(),
                        primaryImg,
                        price,
                        MoneyUtils.formatVnd(price),
                        p.getUnit(),
                        qty,
                        subtotal,
                        MoneyUtils.formatVnd(subtotal),
                        p.getStock(),
                        isOutOfStock
                );

                itemResponses.add(cir);
                if (!isOutOfStock) {
                    groupTotal = groupTotal.add(subtotal);
                }
                totalItemCount += qty;
            }

            shopGroups.add(new ShopCartGroupResponse(
                    entry.getKey(),
                    shopName,
                    shopSlug,
                    provinceName,
                    itemResponses,
                    groupTotal,
                    MoneyUtils.formatVnd(groupTotal)
            ));

            grandTotal = grandTotal.add(groupTotal);
        }

        return new CartSummaryResponse(
                shopGroups,
                totalItemCount,
                grandTotal,
                MoneyUtils.formatVnd(grandTotal)
        );
    }
}

