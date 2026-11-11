package com.vietspecialty.review;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.request.ReviewCreateRequest;
import com.vietspecialty.dto.request.SellerReplyRequest;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.dto.response.ReviewResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.NotificationService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProductReviewAndReplyTest {

    @Autowired
    private ReviewService reviewService;

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

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Test
    @DisplayName("Kiểm thử đánh giá đặc sản: Chặn khi chưa DELIVERED, cho phép sau DELIVERED, cập nhật avgRating & seller phản hồi")
    void testReviewWorkflowAndSellerReply() {
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findAll().get(0);

        // 1. Tạo Seller & Shop
        User seller = userRepository.save(User.builder()
                .fullName("Chủ Lò Mật Ong").email("seller_review@vietspecialty.vn")
                .passwordHash("hash").phone("0988222333").role(Role.SELLER).status(UserStatus.ACTIVE).build());
        Shop shop = shopRepository.save(Shop.builder()
                .name("HTX Mật Ong Rừng").slug("htx-mat-ong-rung")
                .owner(seller).province(province).status(ShopStatus.APPROVED).build());

        // 2. Tạo sản phẩm
        Product product = productRepository.save(Product.builder()
                .shop(shop).category(category).province(province)
                .name("Mật Ong Hoa Bạc Hà").slug("mat-ong-hoa-bac-ha")
                .price(new BigDecimal("350000")).stock(100).soldCount(0)
                .avgRating(BigDecimal.ZERO).reviewCount(0).unit("Chai")
                .status(ProductStatus.ACTIVE).build());

        // 3. Tạo Buyer & Đặt hàng COD
        User buyer = userRepository.save(User.builder()
                .fullName("Trần Khách").email("buyer_review@vietspecialty.vn")
                .passwordHash("hash").phone("0977444555").role(Role.USER).status(UserStatus.ACTIVE).build());
        Address addr = addressRepository.save(Address.builder()
                .user(buyer).receiverName("Trần Khách").receiverPhone("0977444555")
                .province(province).ward("Phường 2").streetAddress("200 Đường B").isDefault(true).build());

        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 2));
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup group = orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                addr.getId(), "COD", "STANDARD", null, null, preview.checkoutToken(), null
        ), "127.0.0.1");

        List<Order> orders = orderRepository.findByOrderGroupId(group.getId());
        Order order = orders.get(0);
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        OrderItem item = items.get(0);

        // 4. Khi đơn đang ở PENDING_CONFIRM -> Thử gửi đánh giá -> Phải thất bại với REVIEW_NOT_ELIGIBLE
        assertThrows(BusinessException.class, () ->
                reviewService.createReview(buyer.getId(), new ReviewCreateRequest(item.getId(), 5, "Hàng quá đỉnh")),
                "Chưa nhận hàng thì không được phép đánh giá"
        );

        // 5. Chuyển trạng thái đơn sang DELIVERED (đã giao thành công)
        orderService.confirmSellerOrder(order.getId(), shop.getId());
        orderService.shipSellerOrder(order.getId(), shop.getId(), "Viettel Post", "VT123");
        orderService.confirmOrderReceived(order.getId(), buyer.getId());

        // 6. Gửi đánh giá 5 sao hợp lệ
        ReviewResponse reviewResponse = reviewService.createReview(buyer.getId(),
                new ReviewCreateRequest(item.getId(), 5, "Mật ong thơm ngát, màu vàng óng, chuẩn vị bạc hà Hà Giang!"));
        assertNotNull(reviewResponse);
        assertEquals(5, reviewResponse.rating());

        // Kiểm tra avgRating và reviewCount của Product đã cập nhật
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(1, updatedProduct.getReviewCount());
        assertEquals(new BigDecimal("5.0"), updatedProduct.getAvgRating());

        // 7. Cố tình gửi đánh giá lại cho cùng orderItem -> Phải bị từ chối
        assertThrows(BusinessException.class, () ->
                reviewService.createReview(buyer.getId(), new ReviewCreateRequest(item.getId(), 4, "Đánh giá lần 2")),
                "Không được đánh giá trùng cho cùng một mặt hàng trong đơn"
        );

        // 8. Seller gửi phản hồi đánh giá
        ReviewResponse replied = reviewService.replyToReview(reviewResponse.id(), shop.getId(),
                new SellerReplyRequest("Cảm ơn anh Trần Khách đã tin dùng đặc sản mật ong bạc hà của HTX!"));
        assertNotNull(replied.sellerReply());
        assertEquals("Cảm ơn anh Trần Khách đã tin dùng đặc sản mật ong bạc hà của HTX!", replied.sellerReply());

        // 9. Lấy danh sách review theo productId
        Page<ReviewResponse> reviews = reviewService.getProductReviews(product.getId(), null, PageRequest.of(0, 10));
        assertEquals(1, reviews.getTotalElements());
        assertEquals(5, reviews.getContent().get(0).rating());
    }
}

