package com.vietspecialty.service.impl;

import com.vietspecialty.config.AppProperties;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CheckoutFormRequest;
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

        PaymentMethod paymentMethod = "VNPAY".equalsIgnoreCase(req.paymentMethod())
                ? PaymentMethod.VNPAY
                : PaymentMethod.COD;

        ShippingMethod shippingMethod = "EXPRESS".equalsIgnoreCase(req.shippingMethod())
                ? ShippingMethod.EXPRESS
                : ShippingMethod.STANDARD;

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

        // 3. Phân nhóm theo Shop để tính phí ship & tách đơn
        Map<Long, List<CartItem>> itemsByShop = new LinkedHashMap<>();
        for (CartItem ci : cartItems) {
            Long shopId = ci.getProduct().getShop().getId();
            itemsByShop.computeIfAbsent(shopId, k -> new ArrayList<>()).add(ci);
        }

        BigDecimal baseShippingFee = (shippingMethod == ShippingMethod.EXPRESS)
                ? appProperties.getShipping().getExpressFee()
                : appProperties.getShipping().getStandardFee();
        BigDecimal freeThreshold = appProperties.getShipping().getFreeStandardThreshold();

        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        BigDecimal shippingTotal = BigDecimal.ZERO;
        BigDecimal shopDiscountTotal = BigDecimal.ZERO;

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

            // Kiểm tra voucher của shop
            if (req.shopVoucherCodes() != null && req.shopVoucherCodes().containsKey(entry.getKey())) {
                String code = req.shopVoucherCodes().get(entry.getKey());
                Voucher sv = voucherService.validateAndGetVoucher(code, entry.getKey(), shopGoodsTotal, userId);
                if (sv != null) {
                    BigDecimal disc = voucherService.calculateDiscount(sv, shopGoodsTotal);
                    shopDiscountTotal = shopDiscountTotal.add(disc);
                    voucherService.useVoucher(sv.getId());
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

        BigDecimal grandTotal = itemsSubtotal.add(shippingTotal).subtract(shopDiscountTotal).subtract(platformDiscountTotal);
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
                .paymentStatus(paymentMethod == PaymentMethod.COD ? OrderGroupPaymentStatus.UNPAID : OrderGroupPaymentStatus.UNPAID)
                .paymentExpiresAt(paymentMethod == PaymentMethod.VNPAY ? LocalDateTime.now().plusMinutes(timeoutMinutes) : null)
                .build();

        OrderGroup savedGroup = orderGroupRepository.save(orderGroup);

        // 5. Tách N đơn hàng (Orders)
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String shippingAddressStr = address.getReceiverName() + " | " + address.getReceiverPhone() + " | " +
                address.getStreetAddress() + ", " + address.getWard() + ", " + address.getProvince().getName();

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

            BigDecimal shopDisc = BigDecimal.ZERO;
            Voucher shopV = null;
            if (req.shopVoucherCodes() != null && req.shopVoucherCodes().containsKey(shopId)) {
                shopV = voucherService.validateAndGetVoucher(req.shopVoucherCodes().get(shopId), shopId, shopGoodsTotal, userId);
                if (shopV != null) {
                    shopDisc = voucherService.calculateDiscount(shopV, shopGoodsTotal);
                }
            }

            // Phân bổ giảm giá sàn theo tỷ lệ tiền hàng của shop
            BigDecimal shopPlatformDisc = BigDecimal.ZERO;
            if (platformDiscountTotal.compareTo(BigDecimal.ZERO) > 0 && itemsSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                shopPlatformDisc = platformDiscountTotal.multiply(shopGoodsTotal)
                        .divide(itemsSubtotal, 0, java.math.RoundingMode.HALF_UP);
            }

            BigDecimal shopTotalAmount = shopGoodsTotal.add(shopShip).subtract(shopDisc).subtract(shopPlatformDisc);
            if (shopTotalAmount.compareTo(BigDecimal.ZERO) < 0) {
                shopTotalAmount = BigDecimal.ZERO;
            }

            String orderCode = CodeGenerator.generateOrderCode();

            Order order = Order.builder()
                    .code(orderCode)
                    .orderGroup(savedGroup)
                    .user(user)
                    .shop(shop)
                    .receiverName(address.getReceiverName())
                    .receiverPhone(address.getReceiverPhone())
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
                    .build();

            Order savedOrder = orderRepository.save(order);

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

            // Gửi thông báo cho Chủ gian hàng
            if (shop.getOwner() != null) {
                notificationService.createNotification(
                        shop.getOwner(),
                        "ORDER_NEW",
                        "Đơn hàng mới #" + savedOrder.getCode(),
                        "Khách hàng " + user.getFullName() + " vừa đặt đơn hàng với tổng trị giá " + MoneyUtils.formatVnd(savedOrder.getTotalAmount()),
                        "/seller/orders/" + savedOrder.getCode()
                );
            }
        }

        // 6. Tạo bản ghi Payment
        Payment payment = Payment.builder()
                .orderGroup(savedGroup)
                .method(PaymentMethod.VNPAY)
                .txnRef(savedGroup.getCode())
                .amount(savedGroup.getGrandTotal())
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

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
        Order order = orderRepository.findByCodeAndShopId(orderCode, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
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
            // Tăng số lượng đã bán cho sản phẩm
            for (OrderItem item : items) {
                productRepository.incrementSoldCount(item.getProduct().getId(), item.getQuantity());
            }
        } else if (newStatus == OrderStatus.CANCELLED) {
            order.setCancelledAt(now);
            order.setCancelReason(cancelReason);
            order.setCancelledBy(actorType);

            List<OrderItem> items = order.getItems();
            if (items == null || items.isEmpty()) {
                items = orderItemRepository.findByOrderId(order.getId());
            }
            // Hoàn lại tồn kho nguyên tử
            for (OrderItem item : items) {
                productRepository.incrementStockAtomic(item.getProduct().getId(), item.getQuantity());
            }

            // Hoàn lại voucher nếu có
            if (order.getShopVoucher() != null) {
                voucherService.releaseVoucher(order.getShopVoucher().getId());
            }
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
    public void expireUnpaidOrderGroups() {
        LocalDateTime now = LocalDateTime.now();
        List<OrderGroup> expiredGroups = orderGroupRepository.findExpiredVnpayGroups(now);

        for (OrderGroup group : expiredGroups) {
            log.info("Expiring unpaid OrderGroup: {}", group.getCode());
            group.setPaymentStatus(OrderGroupPaymentStatus.EXPIRED);
            orderGroupRepository.save(group);

            List<Order> orders = orderRepository.findByOrderGroupId(group.getId());
            for (Order order : orders) {
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setCancelledAt(now);
                    order.setCancelledBy(ActorType.SYSTEM);
                    order.setCancelReason("Đã hết hạn thời gian thanh toán VNPay (15 phút)");
                    orderRepository.save(order);

                    // Hoàn trả kho nguyên tử
                    for (OrderItem item : order.getItems()) {
                        productRepository.incrementStockAtomic(item.getProduct().getId(), item.getQuantity());
                    }

                    if (order.getShopVoucher() != null) {
                        voucherService.releaseVoucher(order.getShopVoucher().getId());
                    }

                    OrderStatusHistory history = OrderStatusHistory.builder()
                            .order(order)
                            .fromStatus(OrderStatus.PENDING_PAYMENT)
                            .toStatus(OrderStatus.CANCELLED)
                            .actorType(ActorType.SYSTEM)
                            .note("Hệ thống tự động hủy do hết hạn thanh toán VNPay")
                            .build();
                    orderStatusHistoryRepository.save(history);
                }
            }

            if (group.getPlatformVoucher() != null) {
                voucherService.releaseVoucher(group.getPlatformVoucher().getId());
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
}
