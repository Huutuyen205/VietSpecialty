package com.vietspecialty.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.ChatRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.ChatResponse;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.ChatService;
import com.vietspecialty.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class GeminiChatbotRagTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User buyer;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findAll().get(0);

        User seller = userRepository.save(User.builder()
                .fullName("Chủ xưởng Trà Tân Cương")
                .email("seller_chat_test@gmail.com")
                .passwordHash("hashed")
                .phone("0987654321")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build());

        Shop shop = shopRepository.save(Shop.builder()
                .name("Trà Tân Cương Chính Hiệu")
                .slug("tra-tan-cuong-chat-test")
                .owner(seller)
                .status(ShopStatus.APPROVED)
                .province(province)
                .addressDetail("Tân Cương, TP Thái Nguyên")
                .phone("0987654321")
                .build());

        Product product = productRepository.save(Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name("Trà Đinh Tân Cương Thượng Hạng")
                .slug("tra-dinh-tan-cuong-thuong-hang")
                .price(BigDecimal.valueOf(850000))
                .stock(100)
                .soldCount(0)
                .avgRating(BigDecimal.ZERO)
                .reviewCount(0)
                .unit("Hộp")
                .status(ProductStatus.ACTIVE)
                .ocopRating(5)
                .shortDescription("Trà đinh nõn Tân Cương OCOP 5 sao chuẩn hương cốm non")
                .description("Trà đinh nõn Tân Cương OCOP 5 sao thượng hạng xuất xứ Thái Nguyên.")
                .build());

        buyer = userRepository.save(User.builder()
                .fullName("Nguyễn Khách Mua AI")
                .email("buyer_chat_test@gmail.com")
                .passwordHash("hashed")
                .phone("0912345678")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        Address address = addressRepository.save(Address.builder()
                .user(buyer)
                .receiverName("Nguyễn Khách Mua AI")
                .receiverPhone("0912345678")
                .province(province)
                .ward("Phường 1")
                .streetAddress("123 Phố Tân Cương")
                .isDefault(true)
                .build());

        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup group = orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                address.getId(), "COD", "STANDARD", null, null, preview.checkoutToken(), null
        ), "127.0.0.1");

        List<Order> orders = orderRepository.findByOrderGroupId(group.getId());
        sampleOrder = orders.get(0);
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        orderRepository.save(sampleOrder);
    }

    @Test
    @DisplayName("Chatbot RAG: Tra cứu đơn hàng thành công khi cung cấp mã đơn có thật")
    void testOrderLookupExistingOrder() {
        ChatRequest request = new ChatRequest("Đơn hàng " + sampleOrder.getCode() + " của tôi đang ở đâu rồi?", null);

        ChatResponse response = chatService.sendMessage(buyer.getId(), request);

        assertNotNull(response);
        assertEquals(ChatIntent.ORDER_LOOKUP, response.intent());
        assertNotNull(response.reply());
        assertTrue(response.reply().contains(sampleOrder.getCode()), "Câu trả lời phải nhắc đến mã đơn");
        assertTrue(response.reply().toLowerCase().contains("vận chuyển")
                || response.reply().contains("Đang giao")
                || response.reply().contains("SHIPPING")
                || response.reply().contains("đang được giao"));
    }

    @Test
    @DisplayName("Chatbot RAG: Tra cứu đơn hàng thông báo không tìm thấy khi mã đơn sai")
    void testOrderLookupNotFoundOrder() {
        ChatRequest request = new ChatRequest("Cho mình hỏi tình trạng đơn ORD-KHONG-CO-THAT-999", null);

        ChatResponse response = chatService.sendMessage(buyer.getId(), request);

        assertNotNull(response);
        assertEquals(ChatIntent.ORDER_LOOKUP, response.intent());
        assertTrue(response.reply().contains("Không tìm thấy") || response.reply().contains("không tìm thấy"));
    }

    @Test
    @DisplayName("Chatbot RAG: Tư vấn sản phẩm tìm thấy đặc sản theo từ khóa và OCOP")
    void testProductAdvice() {
        ChatRequest request = new ChatRequest("Tôi muốn tìm mua trà biếu sếp dịp tết", null);

        ChatResponse response = chatService.sendMessage(null, request);

        assertNotNull(response);
        assertEquals(ChatIntent.PRODUCT_ADVICE, response.intent());
        assertNotNull(response.reply());
        assertFalse(response.suggestedProducts().isEmpty(), "Phải gợi ý sản phẩm trà");
        assertTrue(response.suggestedProducts().stream().anyMatch(p -> p.name().toLowerCase().contains("trà")), "Sản phẩm gợi ý phải là đặc sản trà");
    }

    @Test
    @DisplayName("Chatbot RAG: Trả lời chính sách đổi trả / thanh toán sàn")
    void testPolicyInquiry() {
        ChatRequest request = new ChatRequest("Chính sách đổi trả hàng hóa trên sàn quy định thế nào?", null);

        ChatResponse response = chatService.sendMessage(null, request);

        assertNotNull(response);
        assertEquals(ChatIntent.POLICY, response.intent());
        assertTrue(response.reply().contains("đổi trả") || response.reply().contains("khiếu nại"));
    }

    @Test
    @DisplayName("ChatApiController: POST /api/chat trả về HTTP 200 JSON hợp lệ")
    void testChatApiControllerEndpoint() throws Exception {
        ChatRequest request = new ChatRequest("Sàn VietSpecialty có bán đặc sản gì nổi tiếng?", null);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").isNotEmpty());
    }
}
