package com.vietspecialty.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.ChatRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.ChatResponse;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.ChatService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.util.MoneyUtils;
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
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .weightGram(500)
                .shelfLife("12 tháng kể từ ngày sản xuất")
                .storageInstructions("Bảo quản nơi khô ráo, thoáng mát, buộc kín túi chân không sau khi mở")
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

    @Test
    @DisplayName("Multi-turn Memory: Hội thoại 3 lượt liên tiếp nhớ đúng sản phẩm, trả lời bảo quản/hạn dùng và tính tổng tiền 2 hộp + freeship")
    void testMultiTurnConversationalMemoryAndCalculation() {
        String chatKey = "test_multiturn_memory_001";

        // Lượt 1: Khách hỏi tư vấn Trà Tân Cương Thái Nguyên
        ChatResponse turn1 = chatService.sendMessage(
                buyer.getId(),
                new ChatRequest("Tư vấn giúp mình Trà Tân Cương Thái Nguyên", chatKey)
        );
        assertNotNull(turn1);
        assertEquals(ChatIntent.PRODUCT_ADVICE, turn1.intent());
        assertFalse(turn1.suggestedProducts().isEmpty(), "Lượt 1 phải gợi ý sản phẩm Trà Tân Cương");
        ProductCardResponse firstSuggested = turn1.suggestedProducts().get(0);
        assertTrue(firstSuggested.name().toLowerCase().contains("trà") || firstSuggested.name().toLowerCase().contains("chè"));

        // Lượt 2: Khách hỏi nối tiếp không nhắc lại tên món ("Món này bảo quản thế nào và hạn sử dụng bao lâu?")
        ChatResponse turn2 = chatService.sendMessage(
                buyer.getId(),
                new ChatRequest("Món này bảo quản thế nào và hạn sử dụng bao lâu?", chatKey)
        );
        assertNotNull(turn2);
        assertEquals(ChatIntent.PRODUCT_ADVICE, turn2.intent());
        assertTrue(turn2.reply().contains(firstSuggested.name()),
                "Lượt 2 phải nhớ chính xác tên sản phẩm ở lượt 1: " + firstSuggested.name());
        assertTrue(turn2.reply().toLowerCase().contains("bảo quản") || turn2.reply().toLowerCase().contains("hạn sử dụng"),
                "Lượt 2 phải trả lời chi tiết thông tin bảo quản và hạn sử dụng");

        // Lượt 3: Khách hỏi tính toán ("Nếu mình lấy 2 hộp này thì tổng bao nhiêu tiền và phí ship tính sao?")
        ChatResponse turn3 = chatService.sendMessage(
                buyer.getId(),
                new ChatRequest("Nếu mình lấy 2 hộp này thì tổng bao nhiêu tiền và phí ship tính sao?", chatKey)
        );
        assertNotNull(turn3);
        assertEquals(ChatIntent.PRODUCT_ADVICE, turn3.intent());
        assertTrue(turn3.reply().contains(firstSuggested.name()),
                "Lượt 3 phải nhớ sản phẩm từ ngữ cảnh hội thoại");
        BigDecimal expectedTwoUnits = firstSuggested.price().multiply(BigDecimal.valueOf(2));
        String formattedTotal = MoneyUtils.formatVnd(expectedTwoUnits);
        assertTrue(turn3.reply().contains(formattedTotal),
                "Lượt 3 phải tính đúng tổng tiền của 2 sản phẩm (" + formattedTotal + "). Reply: " + turn3.reply());
        assertTrue(turn3.reply().contains("500.000₫") || turn3.reply().toLowerCase().contains("miễn phí vận chuyển") || turn3.reply().toLowerCase().contains("freeship"),
                "Lượt 3 phải tư vấn chính xác ngưỡng freeship 500.000₫");
    }

    @Test
    @DisplayName("Budget & Live Vouchers RAG: Tư vấn quà biếu dưới 400k và liệt kê đúng mã Voucher ACTIVE trong DB")
    void testBudgetFilteringAndActiveVouchersInquiry() {
        String chatKey = "test_budget_voucher_002";

        // 1. Tư vấn đặc sản theo ngân sách dưới 400k
        ChatResponse budgetRes = chatService.sendMessage(
                null,
                new ChatRequest("Gợi ý đặc sản làm quà biếu dưới 400k", chatKey)
        );
        assertNotNull(budgetRes);
        assertEquals(ChatIntent.PRODUCT_ADVICE, budgetRes.intent());
        assertFalse(budgetRes.suggestedProducts().isEmpty(), "Phải gợi ý các đặc sản phù hợp ngân sách dưới 400k");
        for (ProductCardResponse card : budgetRes.suggestedProducts()) {
            assertTrue(card.price().compareTo(BigDecimal.valueOf(400000)) <= 0,
                    "Mọi sản phẩm gợi ý cho ngân sách dưới 400k phải có giá <= 400.000₫, nhưng gặp: " + card.name() + " (" + card.price() + ")");
        }

        // 2. Hỏi mã giảm giá / voucher đang có trên sàn
        ChatResponse voucherRes = chatService.sendMessage(
                null,
                new ChatRequest("Trên sàn đang có mã giảm giá hay voucher nào không?", chatKey)
        );
        assertNotNull(voucherRes);
        assertNotNull(voucherRes.reply());
        assertTrue(voucherRes.reply().contains("VIETSPECIALTY") || voucherRes.reply().contains("FREESHIP30"),
                "Câu trả lời phải liệt kê đúng mã giảm giá thực tế đang ACTIVE trong DB. Reply: " + voucherRes.reply());
    }

    @Test
    @DisplayName("Domain Guardrails: Từ chối khéo léo câu hỏi ngoài phạm vi (code Python, thời tiết, Bitcoin) và dẫn dắt về đặc sản")
    void testDomainGuardrailsOutOfScopeAndPrivacy() {
        // 1. Hỏi viết code Python giải phương trình bậc 2
        ChatResponse codingRes = chatService.sendMessage(
                null,
                new ChatRequest("Viết cho tôi đoạn code Python giải phương trình bậc 2", "guardrail_code_01")
        );
        assertNotNull(codingRes);
        assertEquals(ChatIntent.OTHER, codingRes.intent());
        assertFalse(codingRes.reply().contains("import math") || codingRes.reply().contains("def "),
                "AI tuyệt đối không được viết code Python giải toán cho khách");
        assertTrue(codingRes.reply().contains("VietSpecialty"),
                "AI phải dẫn dắt khéo léo quay lại sàn đặc sản VietSpecialty");
        assertFalse(codingRes.suggestedProducts().isEmpty(),
                "AI phải gợi ý kèm đặc sản thực tế trên sàn để dẫn dắt khách");

        // 2. Hỏi dự báo thời tiết & giá Bitcoin
        ChatResponse weatherCryptoRes = chatService.sendMessage(
                null,
                new ChatRequest("Dự báo thời tiết ngày mai và giá Bitcoin thế nào?", "guardrail_crypto_02")
        );
        assertNotNull(weatherCryptoRes);
        assertEquals(ChatIntent.OTHER, weatherCryptoRes.intent());
        assertTrue(weatherCryptoRes.reply().contains("VietSpecialty"));
        assertFalse(weatherCryptoRes.suggestedProducts().isEmpty());

        // 3. Khóa bảo mật đơn hàng (Order Privacy Guardrail): Khách chưa đăng nhập tra cứu đơn hàng của người khác -> che SĐT
        ChatResponse guestOrderLookup = chatService.sendMessage(
                null,
                new ChatRequest("Kiểm tra đơn hàng " + sampleOrder.getCode(), "guardrail_privacy_03")
        );
        assertNotNull(guestOrderLookup);
        assertEquals(ChatIntent.ORDER_LOOKUP, guestOrderLookup.intent());
        assertTrue(guestOrderLookup.reply().contains("***"),
                "Khách chưa đăng nhập tra cứu đơn hàng phải được che một phần số điện thoại người nhận");
        assertFalse(guestOrderLookup.reply().contains("0912345678"),
                "Không được lộ toàn bộ số điện thoại người nhận cho người lạ");
    }

    @Test
    @DisplayName("Dynamic Reasoning: Hai câu hỏi khác nhau về chính sách/tính phí ship hoặc sản phẩm không trả về văn bản kịch bản cứng giống nhau")
    void testNoCannedResponsesDynamicReasoning() {
        ChatResponse policyShip = chatService.sendMessage(
                null,
                new ChatRequest("Mua đơn hàng 350k thì phí vận chuyển tiêu chuẩn và giao nhanh tính bao nhiêu tiền?", "dynamic_policy_1")
        );
        ChatResponse policyReturn = chatService.sendMessage(
                null,
                new ChatRequest("Chính sách đổi trả hoàn tiền trong 48h khi đặc sản bị vỡ hỏng như thế nào?", "dynamic_policy_2")
        );

        assertNotNull(policyShip.reply());
        assertNotNull(policyReturn.reply());
        assertNotEquals(policyShip.reply(), policyReturn.reply(),
                "Hai câu hỏi chính sách khác nhau (phí ship đơn 350k vs đổi trả 48h) phải nhận được phân tích riêng biệt, không phải chuỗi văn bản cứng");
        assertTrue(policyShip.reply().contains("150.000₫") || policyShip.reply().contains("30.000₫"),
                "Câu hỏi phí ship cho đơn 350k phải phân tích cụ thể số tiền hoặc mức phí vận chuyển");
        assertTrue(policyReturn.reply().contains("48"),
                "Câu hỏi đổi trả phải phân tích rõ quy định 48 giờ");

        ChatResponse prodTea = chatService.sendMessage(
                null,
                new ChatRequest("Tư vấn đặc sản Trà Thái Nguyên biếu sếp", "dynamic_prod_1")
        );
        ChatResponse prodSnack = chatService.sendMessage(
                null,
                new ChatRequest("Gợi ý mồi nhắm uống bia cuối tuần lai rai", "dynamic_prod_2")
        );
        assertNotEquals(prodTea.reply(), prodSnack.reply(),
                "Hai câu hỏi tư vấn đặc sản khác nhau phải sinh nội dung tư vấn khác nhau");
    }

    @Test
    @DisplayName("Chat History, Reset API & Chat Widget UI: Khôi phục lịch sử kèm suggestedProducts, Reset phiên và hiển thị Context Chips")
    void testChatHistoryWithSuggestedProductsAndResetAndWidgetUi() throws Exception {
        String chatKey = "e2e_history_restore_key_99";

        // Gửi 1 lượt chat có gợi ý sản phẩm
        chatService.sendMessage(buyer.getId(), new ChatRequest("Tư vấn Trà Tân Cương Thái Nguyên", chatKey));

        // GET /api/chat/history phải trả về cả tin nhắn user, bot và danh sách suggestedProducts
        mockMvc.perform(get("/api/chat/history").param("chatKey", chatKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[1].role").value("bot"))
                .andExpect(jsonPath("$.data[1].suggestedProducts").isNotEmpty());

        // POST /api/chat/reset phải tạo chatKey mới
        mockMvc.perform(post("/api/chat/reset")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("chatKey", chatKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chatKey").isNotEmpty());

        // Kiểm tra giao diện chat-widget.html được nhúng trên trang chủ có đầy đủ nút Làm mới, chip gợi ý mới và script loadChatHistory
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"chatbot-reset-btn\"")))
                .andExpect(content().string(containsString("Gợi ý mồi nhắm uống bia cuối tuần")))
                .andExpect(content().string(containsString("Tư vấn combo quà biếu dưới 500k")))
                .andExpect(content().string(containsString("Săn mã giảm giá đang có trên sàn")))
                .andExpect(content().string(containsString("loadChatHistory")));
    }

    @Test
    @DisplayName("Hotfix Regression: Tương thích ngược enum GIFT_ADVICE/FOOD_PAIRING và tư vấn câu hỏi Sức khỏe & Ăn kèm rau muống")
    void testLegacyIntentCompatibilityAndHealthAndVegetablePairingQueries() throws Exception {
        String legacyChatKey = "chat_legacy_intent_regression_test";

        // 1. Giả lập lịch sử cũ trong DB có lưu intent GIFT_ADVICE và FOOD_PAIRING
        assertNotNull(ChatIntent.valueOf("GIFT_ADVICE"));
        assertNotNull(ChatIntent.valueOf("FOOD_PAIRING"));
        assertNotNull(ChatIntent.valueOf("CULINARY_CULTURE"));
        assertNotNull(ChatIntent.valueOf("RECIPE_STORAGE"));
        assertNotNull(ChatIntent.valueOf("MENU_PLANNING"));

        // 2. Khách hỏi "món gì tốt cho sức khỏe"
        ChatResponse healthRes = chatService.sendMessage(
                null,
                new ChatRequest("món gì tốt cho sức khỏe", legacyChatKey)
        );
        assertNotNull(healthRes);
        assertEquals(ChatIntent.PRODUCT_ADVICE, healthRes.intent());
        assertFalse(healthRes.suggestedProducts().isEmpty(), "Phải gợi ý sản phẩm tốt cho sức khỏe");
        assertTrue(healthRes.reply().toLowerCase().contains("sức khỏe") || healthRes.reply().toLowerCase().contains("đề kháng"),
                "Phải phân tích công dụng bồi bổ sức khỏe / đề kháng");

        // 3. Khách hỏi "tôi muốn ăn gì đó với rau muống"
        ChatResponse vegRes = chatService.sendMessage(
                null,
                new ChatRequest("tôi muốn ăn gì đó với rau muống", legacyChatKey)
        );
        assertNotNull(vegRes);
        assertEquals(ChatIntent.PRODUCT_ADVICE, vegRes.intent());
        assertFalse(vegRes.suggestedProducts().isEmpty(), "Phải gợi ý đặc sản ăn kèm rau muống");
        assertTrue(vegRes.reply().toLowerCase().contains("rau muống") || vegRes.reply().toLowerCase().contains("chấm"),
                "Phải tư vấn cách ăn kèm / pha chấm với rau muống");

        // 4. Khách gõ ".." ngắn gọn
        ChatResponse shortDotsRes = chatService.sendMessage(
                null,
                new ChatRequest("..", legacyChatKey)
        );
        assertNotNull(shortDotsRes);
        assertNotNull(shortDotsRes.reply());
        assertTrue(shortDotsRes.reply().length() < 400, "Phản hồi cho '..' phải ngắn gọn, thân thiện");
    }
}
