package com.vietspecialty.cart;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.response.CartSummaryResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.CategoryRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CartServiceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("Thêm sản phẩm vào giỏ hàng và kiểm tra tính toán tổng tiền phân nhóm shop")
    void testAddToCartAndGroupSummary() {
        // 1. Tạo người bán và shop
        User seller = userRepository.save(User.builder()
                .fullName("Người bán 1")
                .email("seller_cart_1@vietspecialty.vn")
                .passwordHash("hash")
                .phone("0911223344")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build());

        Shop shop = shopRepository.save(Shop.builder()
                .name("HTX Cà Phê Buôn Ma Thuột")
                .slug("htx-ca-phe-buon-ma-thuot")
                .owner(seller)
                .province(provinceRepository.findAll().get(0))
                .status(ShopStatus.APPROVED)
                .build());

        // 2. Tạo sản phẩm
        Product product = productRepository.save(Product.builder()
                .shop(shop)
                .category(categoryRepository.findAll().get(0))
                .province(shop.getProvince())
                .name("Cà phê Robusta Đắk Lắk Hạt Rang 500g")
                .slug("ca-phe-robusta-dak-lak-hat-rang-500g")
                .price(new BigDecimal("150000"))
                .stock(50)
                .unit("Gói 500g")
                .status(ProductStatus.ACTIVE)
                .build());

        // 3. Tạo người mua
        User buyer = userRepository.save(User.builder()
                .fullName("Khách mua hàng")
                .email("buyer_cart_1@vietspecialty.vn")
                .passwordHash("hash")
                .phone("0988776655")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        // 4. Thêm 2 gói vào giỏ
        CartSummaryResponse summary = cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 2));
        assertNotNull(summary);
        assertEquals(2, summary.totalItemCount());
        assertEquals(new BigDecimal("300000"), summary.grandTotal());
        assertEquals(1, summary.shopGroups().size());
        assertEquals(shop.getName(), summary.shopGroups().get(0).shopName());

        // 5. Cập nhật số lượng lên 3
        Long cartItemId = summary.shopGroups().get(0).items().get(0).id();
        CartSummaryResponse updated = cartService.updateQuantity(buyer.getId(), cartItemId, 3);
        assertEquals(3, updated.totalItemCount());
        assertEquals(new BigDecimal("450000"), updated.grandTotal());

        // 6. Xóa khỏi giỏ
        CartSummaryResponse empty = cartService.removeFromCart(buyer.getId(), cartItemId);
        assertEquals(0, empty.totalItemCount());
        assertEquals(BigDecimal.ZERO, empty.grandTotal());
    }

    @Test
    @DisplayName("Chặn người bán tự mua sản phẩm của chính shop mình")
    void testCannotBuyOwnProduct() {
        User seller = userRepository.save(User.builder()
                .fullName("Chủ Gian Hàng")
                .email("seller_self_buy@vietspecialty.vn")
                .passwordHash("hash")
                .phone("0933445566")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build());

        Shop shop = shopRepository.save(Shop.builder()
                .name("Gian Hàng Chính Mình")
                .slug("gian-hang-chinh-minh")
                .owner(seller)
                .province(provinceRepository.findAll().get(0))
                .status(ShopStatus.APPROVED)
                .build());

        Product product = productRepository.save(Product.builder()
                .shop(shop)
                .category(categoryRepository.findAll().get(0))
                .province(shop.getProvince())
                .name("Sản phẩm của tôi")
                .slug("san-pham-cua-toi")
                .price(new BigDecimal("100000"))
                .stock(10)
                .unit("Hộp")
                .status(ProductStatus.ACTIVE)
                .build());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                cartService.addToCart(seller.getId(), new CartItemRequest(product.getId(), 1))
        );
        assertEquals(ErrorCode.CANNOT_BUY_OWN_PRODUCT, ex.getErrorCode());
    }
}

