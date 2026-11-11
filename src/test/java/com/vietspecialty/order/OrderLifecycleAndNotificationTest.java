package com.vietspecialty.order;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.dto.response.OrderDetailResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.NotificationService;
import com.vietspecialty.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OrderLifecycleAndNotificationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private NotificationService notificationService;

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

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Vòng đời đơn hàng hoàn chỉnh: Đặt -> Chuẩn bị -> Giao hàng -> Đã nhận -> Kiểm tra tồn kho, bán & thông báo 2 chiều")
    void testCompleteOrderLifecycleAndNotifications() {
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findAll().get(0);

        // 1. Tạo Seller & Shop
        User seller = userRepository.save(User.builder()
                .fullName("Chủ HTX Trà").email("seller_life@vietspecialty.vn")
                .passwordHash("hash").phone("0988111222").role(Role.SELLER).status(UserStatus.ACTIVE).build());
        Shop shop = shopRepository.save(Shop.builder()
                .name("HTX Trà Thái Nguyên").slug("htx-tra-thai-nguyen")
                .owner(seller).province(province).status(ShopStatus.APPROVED).build());

        // 2. Tạo sản phẩm
        Product product = productRepository.save(Product.builder()
                .shop(shop).category(category).province(province)
                .name("Chè Móc Câu").slug("che-moc-cau")
                .price(new BigDecimal("200000")).stock(50).soldCount(0).unit("Hộp")
                .status(ProductStatus.ACTIVE).build());

        // 3. Tạo Buyer & Địa chỉ
        User buyer = userRepository.save(User.builder()
                .fullName("Nguyễn Khách").email("buyer_life@vietspecialty.vn")
                .passwordHash("hash").phone("0977333444").role(Role.USER).status(UserStatus.ACTIVE).build());
        Address addr = addressRepository.save(Address.builder()
                .user(buyer).receiverName("Nguyễn Khách").receiverPhone("0977333444")
                .province(province).ward("Phường 1").streetAddress("100 Đường A").isDefault(true).build());

        // 4. Cho vào giỏ và đặt hàng COD
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 3));
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup group = orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                addr.getId(), "COD", "STANDARD", null, null, preview.checkoutToken(), "Đóng gói cẩn thận"
        ), "127.0.0.1");

        List<Order> orders = orderRepository.findByOrderGroupId(group.getId());
        assertEquals(1, orders.size());
        Order order = orders.get(0);
        assertEquals(OrderStatus.PENDING_CONFIRM, order.getStatus());

        // 5. Kiểm tra thông báo cho Seller (ORDER_NEW)
        long unreadSeller = notificationService.getUnreadCount(seller.getId());
        assertTrue(unreadSeller >= 1, "Seller phải nhận được thông báo có đơn hàng mới");

        // 6. Seller xác nhận đơn (PENDING_CONFIRM -> PREPARING)
        orderService.confirmSellerOrder(order.getId(), shop.getId());
        OrderDetailResponse detailPreparing = orderService.getOrderDetail(order.getCode(), buyer.getId());
        assertEquals(OrderStatus.PREPARING, detailPreparing.status());

        // Kiểm tra thông báo gửi cho Buyer (ORDER_CONFIRMED)
        long unreadBuyer = notificationService.getUnreadCount(buyer.getId());
        assertTrue(unreadBuyer >= 1, "Buyer phải nhận được thông báo đơn hàng đã xác nhận");

        // 7. Seller giao hàng (PREPARING -> SHIPPING)
        orderService.shipSellerOrder(order.getId(), shop.getId(), "Viettel Post", "VTP987654321");
        OrderDetailResponse detailShipping = orderService.getOrderDetail(order.getCode(), buyer.getId());
        assertEquals(OrderStatus.SHIPPING, detailShipping.status());
        assertEquals("Viettel Post", detailShipping.carrierName());
        assertEquals("VTP987654321", detailShipping.trackingCode());

        // 8. Buyer xác nhận nhận hàng (SHIPPING -> DELIVERED)
        orderService.confirmOrderReceived(order.getId(), buyer.getId());
        OrderDetailResponse detailDelivered = orderService.getOrderDetail(order.getCode(), buyer.getId());
        assertEquals(OrderStatus.DELIVERED, detailDelivered.status());
        assertEquals(OrderPaymentStatus.PAID, detailDelivered.paymentStatus(), "Thanh toán COD phải chuyển sang PAID khi giao hàng thành công");

        // 9. Kiểm tra số lượng đã bán của sản phẩm đã tăng thêm 3
        Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(3, reloadedProduct.getSoldCount(), "soldCount của sản phẩm phải tăng từ 0 lên 3");

        // 10. Kiểm tra thông báo đã đọc
        notificationService.markAllAsRead(buyer.getId());
        assertEquals(0, notificationService.getUnreadCount(buyer.getId()), "Số thông báo chưa đọc của buyer phải là 0 sau khi markAllAsRead");

        // 11. Kiểm tra State Machine: Đơn đã DELIVERED không thể chuyển ngược lại PREPARING
        assertThrows(BusinessException.class, () ->
                orderService.changeOrderStatus(order.getId(), OrderStatus.PREPARING, ActorType.SELLER, seller.getId(), "Thử chuyển sai", null)
        );
    }
}

