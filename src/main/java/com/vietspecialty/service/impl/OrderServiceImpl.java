package com.vietspecialty.service.impl;

import com.vietspecialty.config.AppProperties;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.request.ShopPackageGiftRequest;
import com.vietspecialty.dto.response.*;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.mapper.AddressMapper;
import com.vietspecialty.mapper.OrderMapper;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.NotificationService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.VoucherService;
import com.vietspecialty.util.CodeGenerator;
import com.vietspecialty.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final AppProperties appProperties;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final OrderGroupRepository orderGroupRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final VoucherService voucherService;
    private final NotificationService notificationService;

    // Cache tạm thời lưu checkoutToken chống double-click (trong sản xuất có thể dùng Redis)
    private final Set<String> activeCheckoutTokens = ConcurrentHashMap.newKeySet();

    @Override
    public CheckoutPreviewResponse previewCheckout(
            Long userId,
            String shippingMethodStr,
            String systemVoucherCode,
            Map<Long, String> shopVoucherCodes
    ) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        Address defaultAddress = addressRepository.findByUserIdAndIsDefaultTrue(userId)
                .orElseGet(() -> {
                    List<Address> addresses = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
                    return addresses.isEmpty() ? null : addresses.get(0);
                });

        ShippingMethod shippingMethod = "EXPRESS".equalsIgnoreCase(shippingMethodStr)
                ? ShippingMethod.EXPRESS
                : ShippingMethod.STANDARD;

        BigDecimal baseShippingFee = (shippingMethod == ShippingMethod.EXPRESS)
                ? appProperties.getShipping().getExpressFee()
                : appProperties.getShipping().getStandardFee();

        BigDecimal freeThreshold = appProperties.getShipping().getFreeStandardThreshold();

        // Nhóm cart items theo shop
        Map<Long, List<CartItem>> itemsByShop = new LinkedHashMap<>();
        for (CartItem ci : cartItems) {
            Long shopId = ci.getProduct().getShop().getId();
            itemsByShop.computeIfAbsent(shopId, k -> new ArrayList<>()).add(ci);
        }

        List<CheckoutPreviewResponse.ShopCheckoutGroupResponse> shopGroups = new ArrayList<>();
        BigDecimal totalSubtotal = BigDecimal.ZERO;
        BigDecimal totalShippingFee = BigDecimal.ZERO;
        BigDecimal totalShopDiscount = BigDecimal.ZERO;

        for (Map.Entry<Long, List<CartItem>> entry : itemsByShop.entrySet()) {
            Long shopId = entry.getKey();
            List<CartItem> groupItems = entry.getValue();
            Shop shop = groupItems.get(0).getProduct().getShop();

            BigDecimal goodsTotal = BigDecimal.ZERO;
            List<CartItemResponse> itemResponses = new ArrayList<>();

            for (CartItem ci : groupItems) {
                Product p = ci.getProduct();
                BigDecimal subtotal = p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
                goodsTotal = goodsTotal.add(subtotal);

                String primaryImg = (p.getImages() != null && !p.getImages().isEmpty())
                        ? p.getImages().get(0).getImageUrl() : null;

                itemResponses.add(new CartItemResponse(
                        ci.getId(), p.getId(), p.getName(), p.getSlug(), primaryImg,
                        p.getPrice(), MoneyUtils.formatVnd(p.getPrice()), p.getUnit(),
                        ci.getQuantity(), subtotal, MoneyUtils.formatVnd(subtotal),
                        p.getStock(), p.getStock() <= 0
                ));
            }

            // Tính phí ship cho từng shop
            BigDecimal shopShipFee = baseShippingFee;
            if (shippingMethod == ShippingMethod.STANDARD && goodsTotal.compareTo(freeThreshold) >= 0) {
                shopShipFee = BigDecimal.ZERO;
            }

            // Tính voucher của shop nếu có
            BigDecimal shopDiscount = BigDecimal.ZERO;
            if (shopVoucherCodes != null && shopVoucherCodes.containsKey(shopId)) {
                String shopVoucherCode = shopVoucherCodes.get(shopId);
                try {
                    Voucher sv = voucherService.validateAndGetVoucher(shopVoucherCode, shopId, goodsTotal, userId);
                    shopDiscount = voucherService.calculateDiscount(sv, goodsTotal);
                } catch (Exception e) {
                    log.warn("Invalid shop voucher: {}", shopVoucherCode);
                }
            }

            BigDecimal groupTotal = goodsTotal.add(shopShipFee).subtract(shopDiscount);
            if (groupTotal.compareTo(BigDecimal.ZERO) < 0) {
                groupTotal = BigDecimal.ZERO;
            }

            totalSubtotal = totalSubtotal.add(goodsTotal);
            totalShippingFee = totalShippingFee.add(shopShipFee);
            totalShopDiscount = totalShopDiscount.add(shopDiscount);

            shopGroups.add(new CheckoutPreviewResponse.ShopCheckoutGroupResponse(
                    shopId, shop.getName(),
                    shop.getProvince() != null ? shop.getProvince().getName() : "",
                    itemResponses, goodsTotal, MoneyUtils.formatVnd(goodsTotal),
                    shopShipFee, MoneyUtils.formatVnd(shopShipFee),
                    shopDiscount, MoneyUtils.formatVnd(shopDiscount),
                    groupTotal, MoneyUtils.formatVnd(groupTotal)
            ));
        }

        // Tính voucher hệ thống toàn sàn
        BigDecimal systemDiscount = BigDecimal.ZERO;
        if (systemVoucherCode != null && !systemVoucherCode.isBlank()) {
            try {
                Voucher sysVoucher = voucherService.validateAndGetVoucher(systemVoucherCode, null, totalSubtotal, userId);
                systemDiscount = voucherService.calculateDiscount(sysVoucher, totalSubtotal);
            } catch (Exception e) {
                log.warn("Invalid system voucher: {}", systemVoucherCode);
            }
        }

        BigDecimal grandTotal = totalSubtotal.add(totalShippingFee).subtract(totalShopDiscount).subtract(systemDiscount);
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO;
        }

        String checkoutToken = UUID.randomUUID().toString();
        activeCheckoutTokens.add(checkoutToken);

        return new CheckoutPreviewResponse(
                defaultAddress != null ? AddressMapper.toResponse(defaultAddress) : null,
                shopGroups,
                totalSubtotal, MoneyUtils.formatVnd(totalSubtotal),
                totalShippingFee, MoneyUtils.formatVnd(totalShippingFee),
                systemDiscount, MoneyUtils.formatVnd(systemDiscount),
                totalShopDiscount, MoneyUtils.formatVnd(totalShopDiscount),
                grandTotal, MoneyUtils.formatVnd(grandTotal),
                checkoutToken
        );
    }

    @Override
    @Transactional
    public OrderGroup placeOrder(Long userId, CheckoutFormRequest req, String clientIp) {
        // 1. Chống Submit trùng / Double Click
        if (req.checkoutToken() == null || !activeCheckoutTokens.remove(req.checkoutToken())) {
            throw new BusinessException(ErrorCode.CHECKOUT_TOKEN_INVALID);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Address address = addressRepository.findByIdAndUserId(req.addressId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        // Trích xuất thông tin địa chỉ nhận hàng trước khi decrementStockAtomic xóa EntityManager cache
        String receiverName = address.getReceiverName();
        String receiverPhone = address.getReceiverPhone();
        String shippingAddressStr = receiverName + " | " + receiverPhone + " | " +
                address.getStreetAddress() + ", " + address.getWard() + ", " + address.getProvince().getName();

        PaymentMethod paymentMethod = "VNPAY".equalsIgnoreCase(req.paymentMethod())
                ? PaymentMethod.VNPAY
                : PaymentMethod.COD;

        ShippingMethod shippingMethod = "EXPRESS".equalsIgnoreCase(req.shippingMethod())
                ? ShippingMethod.EXPRESS
                : ShippingMethod.STANDARD;

        // Nhóm theo Shop và kiểm tra/chuẩn hóa quà biếu trước khi trừ kho & dùng voucher
        Map<Long, List<CartItem>> itemsByShop = new LinkedHashMap<>();
        for (CartItem ci : cartItems) {
            Long shopId = ci.getProduct().getShop().getId();
            itemsByShop.computeIfAbsent(shopId, k -> new ArrayList<>()).add(ci);
        }

        Map<Long, ResolvedGiftOption> resolvedGifts = new LinkedHashMap<>();
        BigDecimal giftFeeTotal = BigDecimal.ZERO;
        for (Long shopId : itemsByShop.keySet()) {
            ShopPackageGiftRequest giftReq = req.shopGifts() != null ? req.shopGifts().get(shopId) : null;
            ResolvedGiftOption resolved = resolveAndValidateGift(giftReq);
            resolvedGifts.put(shopId, resolved);
            giftFeeTotal = giftFeeTotal.add(resolved.giftFee());
        }

        // 2. Trừ kho nguyên tử - SẮP XẾP TĂNG DẦN THEO PRODUCT ID ĐỂ CHỐNG DEADLOCK
        Map<Long, Integer> productQuantities = new HashMap<>();
        for (CartItem ci : cartItems) {
            productQuantities.merge(ci.getProduct().getId(), ci.getQuantity(), Integer::sum);
        }

        List<Long> sortedProductIds = new ArrayList<>(productQuantities.keySet());
        Collections.sort(sortedProductIds);

        for (Long pid : sortedProductIds) {
            int qty = productQuantities.get(pid);
            int affectedRows = productRepository.decrementStockAtomic(pid, qty);
            if (affectedRows == 0) {
                Product p = productRepository.findById(pid).orElse(null);
                String pName = p != null ? p.getName() : "Sản phẩm";
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Đặc sản \"" + pName + "\" không đủ số lượng trong kho");
            }
        }

        // Tải lại managed User sau khi decrementStockAtomic xóa EntityManager
        user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. Tính phí ship, voucher và tổng tiền theo từng shop

        BigDecimal baseShippingFee = (shippingMethod == ShippingMethod.EXPRESS)
                ? appProperties.getShipping().getExpressFee()
                : appProperties.getShipping().getStandardFee();
        BigDecimal freeThreshold = appProperties.getShipping().getFreeStandardThreshold();

        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        BigDecimal shippingTotal = BigDecimal.ZERO;
        BigDecimal shopDiscountTotal = BigDecimal.ZERO;
        Map<Long, Voucher> validatedShopVouchers = new HashMap<>();
        Map<Long, BigDecimal> shopDiscounts = new HashMap<>();

        for (Map.Entry<Long, List<CartItem>> entry : itemsByShop.entrySet()) {
            BigDecimal shopGoodsTotal = BigDecimal.ZERO;
            for (CartItem ci : entry.getValue()) {
                shopGoodsTotal = shopGoodsTotal.add(ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
            }
            itemsSubtotal = itemsSubtotal.add(shopGoodsTotal);

            BigDecimal shopShip = baseShippingFee;
            if (shippingMethod == ShippingMethod.STANDARD && shopGoodsTotal.compareTo(freeThreshold) >= 0) {
                shopShip = BigDecimal.ZERO;
            }
            shippingTotal = shippingTotal.add(shopShip);

            // Kiểm tra voucher của shop (chỉ validate 1 lần và cache lại cho Bước 5)
            if (req.shopVoucherCodes() != null && req.shopVoucherCodes().containsKey(entry.getKey())) {
                String code = req.shopVoucherCodes().get(entry.getKey());
                Voucher sv = voucherService.validateAndGetVoucher(code, entry.getKey(), shopGoodsTotal, userId);
                if (sv != null) {
                    BigDecimal disc = voucherService.calculateDiscount(sv, shopGoodsTotal);
                    shopDiscountTotal = shopDiscountTotal.add(disc);
                    voucherService.useVoucher(sv.getId());
                    validatedShopVouchers.put(entry.getKey(), sv);
                    shopDiscounts.put(entry.getKey(), disc);
                }
            }
        }

        // Kiểm tra voucher sàn
        BigDecimal platformDiscountTotal = BigDecimal.ZERO;
        Voucher systemVoucher = null;
        if (req.systemVoucherCode() != null && !req.systemVoucherCode().isBlank()) {
            systemVoucher = voucherService.validateAndGetVoucher(req.systemVoucherCode(), null, itemsSubtotal, userId);
            if (systemVoucher != null) {
                platformDiscountTotal = voucherService.calculateDiscount(systemVoucher, itemsSubtotal);
                voucherService.useVoucher(systemVoucher.getId());
            }
        }

        BigDecimal grandTotal = itemsSubtotal.add(shippingTotal).add(giftFeeTotal).subtract(shopDiscountTotal).subtract(platformDiscountTotal);
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO;
        }

        // 4. Tạo OrderGroup
        String groupCode = CodeGenerator.generateOrderGroupCode();
        int timeoutMinutes = appProperties.getOrder().getPaymentTimeoutMinutes();

        OrderGroup orderGroup = OrderGroup.builder()
                .code(groupCode)
                .user(user)
                .paymentMethod(paymentMethod)
                .platformVoucher(systemVoucher)
                .itemsSubtotal(itemsSubtotal)
                .shippingTotal(shippingTotal)
                .shopDiscountTotal(shopDiscountTotal)
                .platformDiscountTotal(platformDiscountTotal)
                .grandTotal(grandTotal)
                .paymentStatus(OrderGroupPaymentStatus.UNPAID)
                .paymentExpiresAt(paymentMethod == PaymentMethod.VNPAY ? LocalDateTime.now().plusMinutes(timeoutMinutes) : null)
                .build();

        OrderGroup savedGroup = orderGroupRepository.save(orderGroup);

        if (systemVoucher != null) {
            voucherUsageRepository.save(VoucherUsage.builder()
                    .voucher(systemVoucher)
                    .user(user)
                    .orderGroup(savedGroup)
                    .order(null)
                    .discountAmount(platformDiscountTotal)
                    .status(VoucherUsageStatus.APPLIED)
                    .build());
        }

        // 5. Tách N đơn hàng (Orders)
        OrderStatus initialStatus = (paymentMethod == PaymentMethod.VNPAY)
                ? OrderStatus.PENDING_PAYMENT
                : OrderStatus.PENDING_CONFIRM;

        for (Map.Entry<Long, List<CartItem>> entry : itemsByShop.entrySet()) {
            Long shopId = entry.getKey();
            List<CartItem> groupItems = entry.getValue();
            Shop shop = groupItems.get(0).getProduct().getShop();

            BigDecimal shopGoodsTotal = BigDecimal.ZERO;
            for (CartItem ci : groupItems) {
                shopGoodsTotal = shopGoodsTotal.add(ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
            }

            BigDecimal shopShip = baseShippingFee;
            if (shippingMethod == ShippingMethod.STANDARD && shopGoodsTotal.compareTo(freeThreshold) >= 0) {
                shopShip = BigDecimal.ZERO;
            }

            Voucher shopV = validatedShopVouchers.get(shopId);
            BigDecimal shopDisc = shopDiscounts.getOrDefault(shopId, BigDecimal.ZERO);

            // Phân bổ giảm giá sàn theo tỷ lệ tiền hàng của shop
            BigDecimal shopPlatformDisc = BigDecimal.ZERO;
            if (platformDiscountTotal.compareTo(BigDecimal.ZERO) > 0 && itemsSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                shopPlatformDisc = platformDiscountTotal.multiply(shopGoodsTotal)
                        .divide(itemsSubtotal, 0, java.math.RoundingMode.HALF_UP);
            }

            ResolvedGiftOption giftOption = resolvedGifts.getOrDefault(shopId, ResolvedGiftOption.nonGift());
            BigDecimal shopGiftFee = giftOption.giftFee();

            BigDecimal shopTotalAmount = shopGoodsTotal.add(shopShip).add(shopGiftFee).subtract(shopDisc).subtract(shopPlatformDisc);
            if (shopTotalAmount.compareTo(BigDecimal.ZERO) < 0) {
                shopTotalAmount = BigDecimal.ZERO;
            }

            String orderCode = CodeGenerator.generateOrderCode();

            Order order = Order.builder()
                    .code(orderCode)
                    .orderGroup(savedGroup)
                    .user(user)
                    .shop(shop)
                    .receiverName(receiverName)
                    .receiverPhone(receiverPhone)
                    .shippingAddress(shippingAddressStr)
                    .shippingMethod(shippingMethod)
                    .itemsSubtotal(shopGoodsTotal)
                    .shippingFee(shopShip)
                    .shopVoucher(shopV)
                    .shopDiscount(shopDisc)
                    .platformDiscount(shopPlatformDisc)
                    .totalAmount(shopTotalAmount)
                    .paymentMethod(paymentMethod)
                    .paymentStatus(OrderPaymentStatus.UNPAID)
                    .status(initialStatus)
                    .note(req.note())
                    .isGift(giftOption.isGift())
                    .giftOccasion(giftOption.giftOccasion())
                    .giftWrapType(giftOption.giftWrapType())
                    .giftFee(shopGiftFee)
                    .giftRecipientName(giftOption.giftRecipientName())
                    .giftMessage(giftOption.giftMessage())
                    .build();

            Order savedOrder = orderRepository.save(order);

            if (shopV != null) {
                voucherUsageRepository.save(VoucherUsage.builder()
                        .voucher(shopV)
                        .user(user)
                        .orderGroup(savedGroup)
                        .order(savedOrder)
                        .discountAmount(shopDisc)
                        .status(VoucherUsageStatus.APPLIED)
                        .build());
            }

            // Tạo các OrderItem
            for (CartItem ci : groupItems) {
                Product p = ci.getProduct();
                BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
                String primaryImg = (p.getImages() != null && !p.getImages().isEmpty())
                        ? p.getImages().get(0).getImageUrl() : null;

                OrderItem orderItem = OrderItem.builder()
                        .order(savedOrder)
                        .product(p)
                        .productName(p.getName())
                        .productImageUrl(primaryImg)
                        .unit(p.getUnit())
                        .unitPrice(p.getPrice())
                        .quantity(ci.getQuantity())
                        .lineTotal(lineTotal)
                        .build();

                OrderItem savedItem = orderItemRepository.save(orderItem);
                savedOrder.getItems().add(savedItem);
            }

            // Ghi lịch sử trạng thái
            OrderStatusHistory history = OrderStatusHistory.builder()
                    .order(savedOrder)
                    .fromStatus(null)
                    .toStatus(initialStatus)
                    .actorType(ActorType.BUYER)
                    .changedBy(user)
                    .note(paymentMethod == PaymentMethod.VNPAY ? "Đặt hàng thành công, chờ thanh toán VNPay" : "Đặt hàng COD thành công, chờ xác nhận")
                    .build();
            orderStatusHistoryRepository.save(history);

            // Gửi thông báo cho Chủ gian hàng (dùng managed reference để tránh detached proxy)
            if (shop.getOwner() != null) {
                User managedOwner = userRepository.getReferenceById(shop.getOwner().getId());
                notificationService.createNotification(
                        managedOwner,
                        "ORDER_NEW",
                        "Đơn hàng mới #" + savedOrder.getCode(),
                        "Khách hàng " + user.getFullName() + " vừa đặt đơn hàng với tổng trị giá " + MoneyUtils.formatVnd(savedOrder.getTotalAmount()),
                        "/seller/orders/" + savedOrder.getCode()
                );
            }
        }

        // 6. Tạo bản ghi Payment (chỉ tạo khi thanh toán qua VNPay)
        if (paymentMethod == PaymentMethod.VNPAY) {
            Payment payment = Payment.builder()
                    .orderGroup(savedGroup)
                    .method(PaymentMethod.VNPAY)
                    .txnRef(savedGroup.getCode())
                    .amount(savedGroup.getGrandTotal())
                    .status(PaymentStatus.PENDING)
                    .build();
            paymentRepository.save(payment);
        }

        // 7. Xóa sạch giỏ hàng của user
        cartItemRepository.deleteAllByUserId(userId);
        log.info("Order placed successfully: groupCode={}, grandTotal={}, paymentMethod={}", groupCode, grandTotal, paymentMethod);

        return savedGroup;
    }

    @Override
    public OrderGroupSummaryResponse getOrderGroupSummary(String groupCode, Long userId) {
        OrderGroup group = orderGroupRepository.findByCodeAndUserId(groupCode, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));

        List<Order> orders = orderRepository.findByOrderGroupId(group.getId());
        List<OrderGroupSummaryResponse.OrderSummaryItem> orderInfos = orders.stream()
                .map(o -> new OrderGroupSummaryResponse.OrderSummaryItem(
                        o.getId(), o.getCode(), o.getShop().getName(), o.getTotalAmount(),
                        MoneyUtils.formatVnd(o.getTotalAmount()), o.getStatus().name()
                ))
                .toList();

        return new OrderGroupSummaryResponse(
                group.getId(), group.getCode(), group.getGrandTotal(),
                MoneyUtils.formatVnd(group.getGrandTotal()),
                group.getPaymentMethod().name(), group.getPaymentStatus().name(),
                orderInfos, group.getCreatedAt()
        );
    }

    @Override
    public OrderDetailResponse getOrderDetail(String orderCode, Long userId) {
        Order order = orderRepository.findByCodeAndUserId(orderCode, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return OrderMapper.toDetail(order);
    }

    @Override
    public OrderDetailResponse getSellerOrderDetail(String orderCode, Long shopId) {
        Optional<Order> orderOpt = orderRepository.findByCodeAndShopId(orderCode, shopId);
        if (orderOpt.isEmpty() && orderCode != null && orderCode.matches("\\d+")) {
            Long orderId = Long.parseLong(orderCode);
            orderOpt = orderRepository.findById(orderId)
                    .filter(o -> o.getShop() != null && o.getShop().getId().equals(shopId));
        }
        Order order = orderOpt.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return OrderMapper.toDetail(order);
    }

    @Override
    public Page<OrderSummaryResponse> getBuyerOrders(Long userId, OrderStatus status, Pageable pageable) {
        Page<Order> orderPage;
        if (status != null) {
            orderPage = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        } else {
            orderPage = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return orderPage.map(OrderMapper::toSummary);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long userId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thao tác trên đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PENDING_CONFIRM) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_ORDER, "Đơn hàng đang chuẩn bị hoặc đang giao, không thể hủy");
        }

        changeOrderStatus(orderId, OrderStatus.CANCELLED, ActorType.BUYER, userId, "Khách hàng hủy đơn", reason);
    }

    @Override
    @Transactional
    public void confirmOrderReceived(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thao tác trên đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, "Chỉ có thể xác nhận đã nhận khi đơn hàng đang được giao");
        }

        changeOrderStatus(orderId, OrderStatus.DELIVERED, ActorType.BUYER, userId, "Khách hàng xác nhận đã nhận hàng", null);
    }

    @Override
    public Page<OrderSummaryResponse> getSellerOrders(Long shopId, OrderStatus status, String search, Pageable pageable) {
        return orderRepository.searchSellerOrders(shopId, status, (search != null && !search.isBlank()) ? search.trim() : null, pageable)
                .map(OrderMapper::toSummary);
    }

    @Override
    @Transactional
    public void confirmSellerOrder(Long orderId, Long shopId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getShop().getId().equals(shopId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Đơn hàng không thuộc gian hàng của bạn");
        }

        User owner = order.getShop().getOwner();
        Long ownerId = owner != null ? owner.getId() : null;
        changeOrderStatus(orderId, OrderStatus.PREPARING, ActorType.SELLER, ownerId, "Người bán xác nhận và chuẩn bị hàng", null);
    }

    @Override
    @Transactional
    public void shipSellerOrder(Long orderId, Long shopId, String carrierName, String trackingCode) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getShop().getId().equals(shopId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Đơn hàng không thuộc gian hàng của bạn");
        }

        order.setCarrierName(carrierName);
        order.setTrackingCode(trackingCode);
        orderRepository.save(order);

        User owner = order.getShop().getOwner();
        Long ownerId = owner != null ? owner.getId() : null;
        changeOrderStatus(orderId, OrderStatus.SHIPPING, ActorType.SELLER, ownerId,
                "Bàn giao đơn vị vận chuyển: " + carrierName + " (" + trackingCode + ")", null);
    }

    @Override
    @Transactional
    public void cancelSellerOrder(Long orderId, Long shopId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getShop().getId().equals(shopId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Đơn hàng không thuộc gian hàng của bạn");
        }

        User owner = order.getShop().getOwner();
        Long ownerId = owner != null ? owner.getId() : null;
        changeOrderStatus(orderId, OrderStatus.CANCELLED, ActorType.SELLER, ownerId, "Người bán hủy đơn", reason);
    }

    @Override
    @Transactional
    public void changeOrderStatus(Long orderId, OrderStatus newStatus, ActorType actorType, Long actorId, String note, String cancelReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        OrderStatus currentStatus = order.getStatus();

        // State Machine validation
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Không thể chuyển trạng thái từ " + currentStatus + " sang " + newStatus);
        }

        order.setStatus(newStatus);
        LocalDateTime now = LocalDateTime.now();

        if (newStatus == OrderStatus.SHIPPING) {
            order.setShippedAt(now);
        } else if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(now);
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                order.setPaymentStatus(OrderPaymentStatus.PAID);
            }
            List<OrderItem> items = order.getItems();
            if (items == null || items.isEmpty()) {
                items = orderItemRepository.findByOrderId(order.getId());
            }
            List<Map.Entry<Long, Integer>> soldUpdates = items.stream()
                    .map(item -> Map.entry(item.getProduct().getId(), item.getQuantity()))
                    .toList();
            orderRepository.saveAndFlush(order);
            // Tăng số lượng đã bán cho sản phẩm
            for (Map.Entry<Long, Integer> entry : soldUpdates) {
                productRepository.incrementSoldCount(entry.getKey(), entry.getValue());
            }
            order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        } else if (newStatus == OrderStatus.CANCELLED) {
            order.setCancelledAt(now);
            order.setCancelReason(cancelReason);
            order.setCancelledBy(actorType);
            if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
                order.setPaymentStatus(OrderPaymentStatus.REFUND_PENDING);
            }

            List<OrderItem> items = order.getItems();
            if (items == null || items.isEmpty()) {
                items = orderItemRepository.findByOrderId(order.getId());
            }
            List<Map.Entry<Long, Integer>> stockRestores = items.stream()
                    .map(item -> Map.entry(item.getProduct().getId(), item.getQuantity()))
                    .toList();
            Long shopVoucherId = order.getShopVoucher() != null ? order.getShopVoucher().getId() : null;
            Long orderGroupId = order.getOrderGroup() != null ? order.getOrderGroup().getId() : null;
            Long platformVoucherId = (order.getOrderGroup() != null && order.getOrderGroup().getPlatformVoucher() != null)
                    ? order.getOrderGroup().getPlatformVoucher().getId()
                    : null;
            orderRepository.saveAndFlush(order);

            // Hoàn lại tồn kho nguyên tử
            for (Map.Entry<Long, Integer> entry : stockRestores) {
                productRepository.incrementStockAtomic(entry.getKey(), entry.getValue());
            }

            // Hoàn lại voucher của shop nếu có
            if (shopVoucherId != null) {
                List<VoucherUsage> orderUsages = voucherUsageRepository.findByOrderId(orderId);
                boolean releasedShopUsage = false;
                for (VoucherUsage vu : orderUsages) {
                    if (vu.getStatus() == VoucherUsageStatus.APPLIED) {
                        vu.setStatus(VoucherUsageStatus.RELEASED);
                        voucherUsageRepository.save(vu);
                        releasedShopUsage = true;
                    }
                }
                if (releasedShopUsage || orderUsages.isEmpty()) {
                    voucherService.releaseVoucher(shopVoucherId);
                }
            }
            // Chỉ hoàn lại voucher sàn khi toàn bộ đơn con trong OrderGroup đã bị hủy
            if (platformVoucherId != null && orderGroupId != null) {
                boolean allOrdersCancelled = orderRepository.findByOrderGroupId(orderGroupId).stream()
                        .allMatch(o -> o.getStatus() == OrderStatus.CANCELLED);
                if (allOrdersCancelled) {
                    List<VoucherUsage> groupPlatformUsages = voucherUsageRepository.findByOrderGroupId(orderGroupId).stream()
                            .filter(vu -> vu.getOrder() == null)
                            .toList();
                    boolean releasedPlatformUsage = false;
                    for (VoucherUsage vu : groupPlatformUsages) {
                        if (vu.getStatus() == VoucherUsageStatus.APPLIED) {
                            vu.setStatus(VoucherUsageStatus.RELEASED);
                            voucherUsageRepository.save(vu);
                            releasedPlatformUsage = true;
                        }
                    }
                    if (releasedPlatformUsage || groupPlatformUsages.isEmpty()) {
                        voucherService.releaseVoucher(platformVoucherId);
                    }
                }
            }
            order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        }

        orderRepository.save(order);

        User actorUser = actorId != null ? userRepository.findById(actorId).orElse(null) : null;

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(currentStatus)
                .toStatus(newStatus)
                .actorType(actorType)
                .changedBy(actorUser)
                .note(note)
                .build();
        orderStatusHistoryRepository.save(history);

        // Gửi thông báo
        if (newStatus == OrderStatus.PREPARING) {
            notificationService.createNotification(
                    order.getUser(),
                    "ORDER_CONFIRMED",
                    "Đơn hàng #" + order.getCode() + " đã được xác nhận",
                    "Gian hàng " + order.getShop().getName() + " đã xác nhận và đang đóng gói sản phẩm.",
                    "/orders/" + order.getCode()
            );
        } else if (newStatus == OrderStatus.SHIPPING) {
            String carrierInfo = (order.getCarrierName() != null ? order.getCarrierName() : "đơn vị vận chuyển") +
                    (order.getTrackingCode() != null ? " (Mã vận đơn: " + order.getTrackingCode() + ")" : "");
            notificationService.createNotification(
                    order.getUser(),
                    "ORDER_SHIPPING",
                    "Đơn hàng #" + order.getCode() + " đang được giao",
                    "Kiện hàng của bạn đã được bàn giao cho " + carrierInfo,
                    "/orders/" + order.getCode()
            );
        } else if (newStatus == OrderStatus.DELIVERED) {
            notificationService.createNotification(
                    order.getUser(),
                    "ORDER_DELIVERED",
                    "Đơn hàng #" + order.getCode() + " đã giao thành công",
                    "Cảm ơn bạn đã mua đặc sản tại VietSpecialty. Hãy để lại đánh giá cho sản phẩm nhé!",
                    "/orders/" + order.getCode()
            );
            if (order.getShop() != null && order.getShop().getOwner() != null) {
                notificationService.createNotification(
                        order.getShop().getOwner(),
                        "ORDER_COMPLETED",
                        "Đơn hàng #" + order.getCode() + " hoàn thành",
                        "Đơn hàng đã được giao thành công tới người nhận.",
                        "/seller/orders/" + order.getCode()
                );
            }
        } else if (newStatus == OrderStatus.CANCELLED) {
            if (actorType == ActorType.BUYER) {
                if (order.getShop() != null && order.getShop().getOwner() != null) {
                    notificationService.createNotification(
                            order.getShop().getOwner(),
                            "ORDER_CANCELLED",
                            "Đơn hàng #" + order.getCode() + " đã bị hủy bởi khách hàng",
                            "Lý do hủy: " + (cancelReason != null ? cancelReason : "Không có lý do"),
                            "/seller/orders/" + order.getCode()
                    );
                }
            } else {
                notificationService.createNotification(
                        order.getUser(),
                        "ORDER_CANCELLED",
                        "Đơn hàng #" + order.getCode() + " đã bị hủy",
                        "Lý do hủy: " + (cancelReason != null ? cancelReason : "Hệ thống hoặc người bán hủy"),
                        "/orders/" + order.getCode()
                );
            }
        }

        log.info("Order {} status changed: {} -> {} by {}", order.getCode(), currentStatus, newStatus, actorType);
    }

    @Override
    @Transactional
    public void confirmRefund(Long orderId, Long adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getPaymentStatus() != OrderPaymentStatus.REFUND_PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn hàng không ở trạng thái chờ hoàn tiền");
        }

        order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
        orderRepository.save(order);

        User adminUser = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(order.getStatus())
                .toStatus(order.getStatus())
                .actorType(ActorType.ADMIN)
                .changedBy(adminUser)
                .note("Quản trị viên đã xác nhận hoàn tiền thành công cho đơn hàng")
                .build();
        orderStatusHistoryRepository.save(history);

        if (order.getUser() != null) {
            notificationService.createNotification(
                    order.getUser(),
                    "ORDER_REFUNDED",
                    "Hoàn tiền thành công đơn hàng #" + order.getCode(),
                    "Số tiền " + MoneyUtils.formatVnd(order.getTotalAmount()) + " của đơn hàng #" + order.getCode() + " đã được hoàn trả thành công.",
                    "/orders/" + order.getCode()
            );
        }

        log.info("Refund confirmed for orderId={}, code={} by adminId={}", order.getId(), order.getCode(), adminId);
    }

    @Override
    @Transactional
    public void expireUnpaidOrderGroups() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> expiredGroupIds = orderGroupRepository.findExpiredVnpayGroups(now).stream()
                .map(OrderGroup::getId)
                .toList();

        for (Long groupId : expiredGroupIds) {
            OrderGroup group = orderGroupRepository.findById(groupId).orElse(null);
            if (group == null) {
                continue;
            }
            log.info("Expiring unpaid OrderGroup: {}", group.getCode());
            Long platformVoucherId = group.getPlatformVoucher() != null ? group.getPlatformVoucher().getId() : null;
            group.setPaymentStatus(OrderGroupPaymentStatus.EXPIRED);
            orderGroupRepository.saveAndFlush(group);

            List<Long> orderIds = orderRepository.findByOrderGroupId(groupId).stream()
                    .map(Order::getId)
                    .toList();
            boolean anyOrderExpired = false;
            for (Long orderId : orderIds) {
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                    continue;
                }
                anyOrderExpired = true;
                Long shopVoucherId = order.getShopVoucher() != null ? order.getShopVoucher().getId() : null;
                List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
                List<Map.Entry<Long, Integer>> stockRestores = orderItems.stream()
                        .map(item -> Map.entry(item.getProduct().getId(), item.getQuantity()))
                        .toList();

                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(now);
                order.setCancelledBy(ActorType.SYSTEM);
                order.setCancelReason("Đã hết hạn thời gian thanh toán VNPay (15 phút)");
                orderRepository.saveAndFlush(order);

                // Hoàn trả kho nguyên tử (clearAutomatically = true sẽ xóa PersistenceContext)
                for (Map.Entry<Long, Integer> entry : stockRestores) {
                    productRepository.incrementStockAtomic(entry.getKey(), entry.getValue());
                }

                if (shopVoucherId != null) {
                    List<VoucherUsage> orderUsages = voucherUsageRepository.findByOrderId(orderId);
                    boolean releasedShopUsage = false;
                    for (VoucherUsage vu : orderUsages) {
                        if (vu.getStatus() == VoucherUsageStatus.APPLIED) {
                            vu.setStatus(VoucherUsageStatus.RELEASED);
                            voucherUsageRepository.saveAndFlush(vu);
                            releasedShopUsage = true;
                        }
                    }
                    if (releasedShopUsage || orderUsages.isEmpty()) {
                        voucherService.releaseVoucher(shopVoucherId);
                    }
                }

                Order managedOrder = orderRepository.getReferenceById(orderId);
                OrderStatusHistory history = OrderStatusHistory.builder()
                        .order(managedOrder)
                        .fromStatus(OrderStatus.PENDING_PAYMENT)
                        .toStatus(OrderStatus.CANCELLED)
                        .actorType(ActorType.SYSTEM)
                        .note("Hệ thống tự động hủy do hết hạn thanh toán VNPay")
                        .build();
                orderStatusHistoryRepository.saveAndFlush(history);
            }

            if (platformVoucherId != null && anyOrderExpired) {
                List<VoucherUsage> groupPlatformUsages = voucherUsageRepository.findByOrderGroupId(groupId).stream()
                        .filter(vu -> vu.getOrder() == null)
                        .toList();
                boolean releasedPlatformUsage = false;
                for (VoucherUsage vu : groupPlatformUsages) {
                    if (vu.getStatus() == VoucherUsageStatus.APPLIED) {
                        vu.setStatus(VoucherUsageStatus.RELEASED);
                        voucherUsageRepository.saveAndFlush(vu);
                        releasedPlatformUsage = true;
                    }
                }
                if (releasedPlatformUsage || groupPlatformUsages.isEmpty()) {
                    voucherService.releaseVoucher(platformVoucherId);
                }
            }
        }
    }

    @Override
    @Transactional
    public void autoDeliverOrders() {
        int days = appProperties.getOrder().getAutoDeliverDays();
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(days);
        List<Order> orders = orderRepository.findOrdersForAutoDeliver(cutoffTime);

        for (Order order : orders) {
            log.info("Auto delivering order: {} after {} days of shipping", order.getCode(), days);
            changeOrderStatus(order.getId(), OrderStatus.DELIVERED, ActorType.SYSTEM, null,
                    "Hệ thống tự động xác nhận đã giao hàng sau " + days + " ngày vận chuyển", null);
        }
    }

    private ResolvedGiftOption resolveAndValidateGift(ShopPackageGiftRequest entry) {
        if (entry == null || !Boolean.TRUE.equals(entry.getIsGift())) {
            return ResolvedGiftOption.nonGift();
        }

        String rawOccasion = entry.getGiftOccasion() != null ? entry.getGiftOccasion().trim() : "";
        if (rawOccasion.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn dịp tặng quà cho gói quà biếu");
        }
        GiftOccasion occasion;
        try {
            occasion = GiftOccasion.valueOf(rawOccasion.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dịp tặng quà không hợp lệ: " + rawOccasion);
        }

        String rawWrapType = entry.getGiftWrapType() != null ? entry.getGiftWrapType().trim() : "";
        GiftWrapType wrapType;
        if (rawWrapType.isEmpty()) {
            wrapType = GiftWrapType.STANDARD_BOX;
        } else {
            try {
                wrapType = GiftWrapType.valueOf(rawWrapType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Hình thức gói quà không hợp lệ: " + rawWrapType);
            }
        }

        String recipientName = entry.getGiftRecipientName() != null
                ? entry.getGiftRecipientName().replace("\r\n", " ").replace("\r", " ").replace("\n", " ").trim()
                : "";
        if (recipientName.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên người nhận quà tối đa 100 ký tự");
        }
        String normalizedRecipient = recipientName.isEmpty() ? null : recipientName;

        String message = entry.getGiftMessage() != null
                ? entry.getGiftMessage().replace("\r\n", "\n").replace("\r", "\n").trim()
                : "";
        if (message.length() > 300) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lời chúc trên thiệp tối đa 300 ký tự");
        }
        String normalizedMessage = message.isEmpty() ? null : message;

        BigDecimal fee = wrapType.getFee() != null ? wrapType.getFee() : BigDecimal.ZERO;
        return new ResolvedGiftOption(true, occasion, wrapType, fee, normalizedRecipient, normalizedMessage);
    }

    private record ResolvedGiftOption(
            boolean isGift,
            GiftOccasion giftOccasion,
            GiftWrapType giftWrapType,
            BigDecimal giftFee,
            String giftRecipientName,
            String giftMessage
    ) {
        static ResolvedGiftOption nonGift() {
            return new ResolvedGiftOption(false, null, null, BigDecimal.ZERO, null, null);
        }
    }
}
