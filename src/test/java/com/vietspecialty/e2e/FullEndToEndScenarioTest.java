package com.vietspecialty.e2e;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.ChatRequest;
import com.vietspecialty.dto.response.ChatResponse;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.request.ReviewCreateRequest;
import com.vietspecialty.dto.request.SellerReplyRequest;
import com.vietspecialty.dto.response.CartSummaryResponse;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.ReviewResponse;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.*;
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
class FullEndToEndScenarioTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Test
    @DisplayName("KỊCH BẢN ĐẦU-CUỐI: Mua hàng đa shop -> Tách đơn -> Vận chuyển -> Đánh giá -> Chatbot tra cứu -> Doanh thu")
    void testFullE2EFlow() {
        // BƯỚC 1: Lấy tài khoản Demo có sẵn
        User buyer = userRepository.findByEmailIgnoreCase("buyer@vietspecialty.vn").orElseThrow();
        User sellerBac = userRepository.findByEmailIgnoreCase("seller_bac@vietspecialty.vn").orElseThrow();

        Shop shopBac = shopRepository.findByOwnerId(sellerBac.getId()).orElseThrow();
        Province province = provinceRepository.findAll().get(0);

        Address address = addressRepository.findAll().stream()
                .filter(a -> a.getUser().getId().equals(buyer.getId()))
                .findFirst()
                .orElseGet(() -> addressRepository.save(Address.builder()
                        .user(buyer)
                        .receiverName("Nguyễn Khách Mua")
                        .receiverPhone("0912345678")
                        .province(province)
                        .ward("Phường Tràng Tiền")
                        .streetAddress("Số 1 Tràng Tiền, Hoàn Kiếm, Hà Nội")
                        .isDefault(true)
                        .build()));

        // BƯỚC 2: Chọn sản phẩm từ 2 gian hàng khác nhau (lọc sản phẩm ACTIVE, còn hàng, shop APPROVED)
        List<Product> activeProducts = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE
                        && p.getStock() != null && p.getStock() > 0
                        && p.getShop() != null && p.getShop().getStatus() == ShopStatus.APPROVED
                        && (p.getShop().getOwner() == null || !p.getShop().getOwner().getId().equals(buyer.getId())))
                .toList();
        assertTrue(activeProducts.size() >= 2, "Cần có ít nhất 2 sản phẩm ACTIVE còn hàng trong hệ thống");

        Product product1 = activeProducts.stream()
                .filter(p -> p.getStock() >= 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cần có ít nhất 1 sản phẩm ACTIVE có tồn kho >= 2")); // Thuộc 1 shop
        Product product2 = activeProducts.stream()
                .filter(p -> !p.getId().equals(product1.getId())
                        && !p.getShop().getId().equals(product1.getShop().getId()))
                .findFirst()
                .orElseGet(() -> activeProducts.stream()
                        .filter(p -> !p.getId().equals(product1.getId()))
                        .findFirst()
                        .orElseThrow()); // Thuộc shop khác (nếu có)

        // Làm sạch giỏ hàng trong phạm vi transaction test để tránh xung đột dữ liệu có sẵn
        cartService.clearCart(buyer.getId());

        // BƯỚC 3: Thêm vào giỏ hàng
        cartService.addToCart(buyer.getId(), new CartItemRequest(product1.getId(), 2));
        cartService.addToCart(buyer.getId(), new CartItemRequest(product2.getId(), 1));

        CartSummaryResponse cart = cartService.getCart(buyer.getId());
        assertNotNull(cart);
        assertTrue(cart.totalItemCount() >= 3);

        // BƯỚC 4: Xem trước đơn hàng (Checkout Preview)
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        assertNotNull(preview);
        assertNotNull(preview.checkoutToken());
        assertTrue(preview.grandTotal().compareTo(BigDecimal.ZERO) > 0);

        // BƯỚC 5: Đặt hàng (Place Order)
        CheckoutFormRequest checkoutForm = new CheckoutFormRequest(
                address.getId(),
                "COD",
                "STANDARD",
                null,
                null,
                preview.checkoutToken(),
                "Giao giờ hành chính giúp tôi"
        );

        OrderGroup orderGroup = orderService.placeOrder(buyer.getId(), checkoutForm, "127.0.0.1");
        assertNotNull(orderGroup);
        assertNotNull(orderGroup.getCode());

        // Kiểm tra đơn hàng được tách ra theo gian hàng
        List<Order> orders = orderRepository.findByOrderGroupId(orderGroup.getId());
        assertFalse(orders.isEmpty());

        Order firstOrder = orders.get(0);
        assertEquals(OrderStatus.PENDING_CONFIRM, firstOrder.getStatus());

        // BƯỚC 6: Người bán xử lý vòng đời đơn hàng: Xác nhận -> Giao hàng -> Khách xác nhận đã nhận
        orderService.confirmSellerOrder(firstOrder.getId(), firstOrder.getShop().getId());
        orderService.shipSellerOrder(firstOrder.getId(), firstOrder.getShop().getId(), "Giao Hàng Tiết Kiệm", "GHTK888999");
        orderService.confirmOrderReceived(firstOrder.getId(), buyer.getId());

        Order updatedOrder = orderRepository.findById(firstOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.DELIVERED, updatedOrder.getStatus());

        // BƯỚC 7: Khách hàng đánh giá sản phẩm sau khi đơn giao thành công
        List<OrderItem> items = orderItemRepository.findByOrderId(updatedOrder.getId());
        assertFalse(items.isEmpty());
        OrderItem orderItem = items.get(0);

        ReviewCreateRequest reviewReq = new ReviewCreateRequest(
                orderItem.getId(),
                5,
                "Đặc sản cực kỳ thơm ngon, đóng gói cẩn thận 5 sao!"
        );
        ReviewResponse review = reviewService.createReview(buyer.getId(), reviewReq);
        assertNotNull(review);
        assertEquals(5, review.rating());

        // BƯỚC 8: Người bán phản hồi đánh giá của khách
        SellerReplyRequest replyReq = new SellerReplyRequest("Dạ cảm ơn quý khách đã tin tưởng ủng hộ đặc sản quê hương ạ!");
        ReviewResponse repliedReview = reviewService.replyToReview(review.id(), firstOrder.getShop().getId(), replyReq);
        assertNotNull(repliedReview.sellerReply());

        // BƯỚC 9: Chatbot AI Gemini RAG tra cứu tình trạng đơn hàng
        ChatRequest chatReq = new ChatRequest(
                "Đơn hàng " + firstOrder.getCode() + " của tôi thế nào rồi?",
                null
        );
        ChatResponse chatRes = chatService.sendMessage(buyer.getId(), chatReq);
        assertNotNull(chatRes);
        assertEquals(ChatIntent.ORDER_LOOKUP, chatRes.intent());
        assertTrue(chatRes.reply().contains(firstOrder.getCode()));

        // BƯỚC 10: Thống kê Doanh thu Dashboard
        DashboardSummaryResponse sellerStats = analyticsService.getSellerDashboardSummary(firstOrder.getShop().getId());
        assertNotNull(sellerStats);
        assertTrue(sellerStats.totalRevenue().compareTo(BigDecimal.ZERO) >= 0);
    }
}
