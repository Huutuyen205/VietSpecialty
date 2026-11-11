package com.vietspecialty.service.impl;

import com.vietspecialty.config.AppProperties;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.ChatIntent;
import com.vietspecialty.domain.enums.ChatRole;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.dto.request.ChatRequest;
import com.vietspecialty.dto.response.ChatResponse;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.mapper.ProductMapper;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.ChatService;
import com.vietspecialty.service.ai.GeminiClient;
import com.vietspecialty.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private final AppProperties appProperties;
    private final GeminiClient geminiClient;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(?i)\\b(VS|VSG|ORD)[0-9A-Z-]{4,20}\\b");

    @Override
    @Transactional
    public ChatResponse sendMessage(Long userId, ChatRequest request) {
        String msg = request.message().trim();
        String chatKey = (request.chatKey() != null && !request.chatKey().isBlank())
                ? request.chatKey().trim()
                : UUID.randomUUID().toString();

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        // 1. Lấy hoặc tạo ChatConversation
        ChatConversation conversation = chatConversationRepository.findByChatKey(chatKey)
                .orElseGet(() -> {
                    ChatConversation c = ChatConversation.builder()
                            .chatKey(chatKey)
                            .user(user)
                            .lastMessageAt(LocalDateTime.now())
                            .build();
                    return chatConversationRepository.save(c);
                });

        // 2. Pass 1: Phân loại ý định (Intent Classifier)
        ChatIntent intent = classifyIntent(msg);

        // 3. Retrieval Context & Sinh câu trả lời
        List<ProductCardResponse> suggestedProducts = new ArrayList<>();
        List<String> suggestedQuestions = new ArrayList<>();
        String replyText;

        switch (intent) {
            case ORDER_LOOKUP -> {
                replyText = handleOrderLookup(msg, user);
                suggestedQuestions = List.of("Chính sách đổi trả khi hàng lỗi?", "Phí vận chuyển tính thế nào?", "Tư vấn đặc sản quà biếu");
            }
            case POLICY -> {
                replyText = handlePolicyInquiry(msg);
                suggestedQuestions = List.of("Gợi ý đặc sản Tây Bắc", "Gợi ý đặc sản Miền Trung", "Cách tra cứu đơn hàng");
            }
            case PRODUCT_ADVICE -> {
                replyText = handleProductAdvice(msg, suggestedProducts);
                suggestedQuestions = List.of("Đặc sản này bảo quản được bao lâu?", "Có mã giảm giá nào áp dụng được không?", "Phí ship về tỉnh bao nhiêu?");
            }
            default -> {
                replyText = handleGeneralChat(msg, suggestedProducts);
                suggestedQuestions = List.of("Gợi ý đặc sản làm quà biếu", "Trà Tân Cương Thái Nguyên", "Tra cứu đơn hàng của tôi");
            }
        }

        // 4. Lưu tin nhắn người dùng và bot vào DB
        ChatMessage userMsg = ChatMessage.builder()
                .conversation(conversation)
                .role(ChatRole.USER)
                .content(msg)
                .intent(intent)
                .build();
        chatMessageRepository.save(userMsg);

        String productIdsStr = suggestedProducts.stream()
                .map(p -> String.valueOf(p.id()))
                .collect(Collectors.joining(","));

        ChatMessage botMsg = ChatMessage.builder()
                .conversation(conversation)
                .role(ChatRole.BOT)
                .content(replyText)
                .intent(intent)
                .suggestedProductIds(productIdsStr)
                .build();
        chatMessageRepository.save(botMsg);

        conversation.setLastMessageAt(LocalDateTime.now());
        chatConversationRepository.save(conversation);

        return new ChatResponse(replyText, intent, chatKey, suggestedProducts, suggestedQuestions);
    }

    private ChatIntent classifyIntent(String msg) {
        String lower = msg.toLowerCase();

        Matcher m = ORDER_CODE_PATTERN.matcher(msg);
        if (m.find() || lower.contains("đơn hàng") || lower.contains("mã đơn") || lower.contains("tra cứu") || lower.contains("kiện hàng")) {
            return ChatIntent.ORDER_LOOKUP;
        }

        if (lower.contains("vận chuyển") || lower.contains("phí ship") || lower.contains("bao lâu nhận được") ||
                lower.contains("đổi trả") || lower.contains("hoàn tiền") || lower.contains("chính sách") || lower.contains("thanh toán")) {
            return ChatIntent.POLICY;
        }

        if (lower.contains("đặc sản") || lower.contains("quà") || lower.contains("chè") || lower.contains("trà") ||
                lower.contains("mật ong") || lower.contains("nước mắm") || lower.contains("bánh") || lower.contains("kẹo") ||
                lower.contains("nem") || lower.contains("hạt") || lower.contains("tây bắc") || lower.contains("miền trung") ||
                lower.contains("miền nam") || lower.contains("ngon") || lower.contains("giá bao nhiêu") || lower.contains("mua")) {
            return ChatIntent.PRODUCT_ADVICE;
        }

        return ChatIntent.OTHER;
    }

    private String handleOrderLookup(String msg, User user) {
        Matcher m = ORDER_CODE_PATTERN.matcher(msg);
        if (m.find()) {
            String code = m.group();
            Optional<Order> orderOpt = orderRepository.findByCode(code);
            if (orderOpt.isPresent()) {
                Order o = orderOpt.get();
                String carrier = o.getCarrierName() != null ? o.getCarrierName() : "Đơn vị vận chuyển liên kết";
                String tracking = o.getTrackingCode() != null ? " (Mã vận đơn: " + o.getTrackingCode() + ")" : "";
                return String.format(
                        "Dạ, VietSpecialty đã tra cứu thấy đơn hàng #%s của bạn:\n" +
                        "● Trạng thái hiện tại: %s\n" +
                        "● Gian hàng gửi: %s\n" +
                        "● Người nhận: %s (%s)\n" +
                        "● Vận chuyển: %s%s\n" +
                        "● Tổng thanh toán: %s (%s)\n\n" +
                        "Bạn có thể vào mục \"Đơn hàng của tôi\" để xem chi tiết hành trình di chuyển của kiện hàng nhé!",
                        o.getCode(),
                        o.getStatus().getDescription(),
                        o.getShop() != null ? o.getShop().getName() : "Gian hàng đặc sản",
                        o.getReceiverName(),
                        o.getReceiverPhone(),
                        carrier,
                        tracking,
                        MoneyUtils.formatVnd(o.getTotalAmount()),
                        o.getPaymentStatus().name().equals("PAID") ? "Đã thanh toán" : "Thu hộ COD"
                );
            } else {
                return "Dạ, VietSpecialty không tìm thấy đơn hàng nào có mã \"" + code + "\". Bạn vui lòng kiểm tra lại chính xác mã đơn (ví dụ: VS261111XXXXX) trong email xác nhận hoặc danh sách đơn mua nhé!";
            }
        }

        if (user != null) {
            var recentOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 2));
            if (!recentOrders.isEmpty()) {
                Order latest = recentOrders.getContent().get(0);
                return String.format(
                        "Dạ chào bạn, đơn hàng gần nhất của bạn là #%s (Trạng thái: %s, Trị giá: %s). " +
                        "Nếu bạn cần tra cứu đơn khác, hãy nhắn cho mình mã đơn cụ thể nhé!",
                        latest.getCode(), latest.getStatus().getDescription(), MoneyUtils.formatVnd(latest.getTotalAmount())
                );
            }
        }

        return "Dạ, để tra cứu tình trạng giao hàng, bạn vui lòng cung cấp mã đơn hàng (bắt đầu bằng VS... ví dụ: VS261111ABCDE) để mình kiểm tra ngay cho bạn nhé!";
    }

    private String handlePolicyInquiry(String msg) {
        return "🌾 **Chính sách Mua sắm & Giao hàng tại VietSpecialty:**\n\n" +
                "1. **Phí vận chuyển:**\n" +
                "   ● Miễn phí vận chuyển tiêu chuẩn cho đơn hàng từ **500.000₫** tại mỗi gian hàng.\n" +
                "   ● Phí tiêu chuẩn: 30.000₫/kiện. Hỏa tốc đặc sản: 50.000₫/kiện.\n\n" +
                "2. **Thời gian giao hàng:**\n" +
                "   ● Nội tỉnh / lân cận: 1 - 2 ngày làm việc.\n" +
                "   ● Toàn quốc: 2 - 4 ngày làm việc (được đóng gói chống sốc và hút chân không cẩn thận).\n\n" +
                "3. **Chính sách đổi trả:**\n" +
                "   ● Đổi trả miễn phí trong vòng **48 giờ** nếu đặc sản bị hư hỏng do vận chuyển hoặc không đúng mô tả OCOP.\n\n" +
                "4. **Hình thức thanh toán:**\n" +
                "   ● Nhận hàng trả tiền mặt (COD) hoặc Thanh toán trực tuyến an toàn qua cổng VNPay (ATM / QR Pay).";
    }

    private String handleProductAdvice(String msg, List<ProductCardResponse> suggestedProducts) {
        // Tìm kiếm các sản phẩm trong kho phù hợp
        String keyword = extractKeyword(msg);
        List<Product> products = productRepository.findAll((root, query, cb) ->
                cb.and(
                        cb.equal(root.get("status"), ProductStatus.ACTIVE),
                        cb.or(
                                cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"),
                                cb.like(cb.lower(root.get("shortDescription")), "%" + keyword.toLowerCase() + "%")
                        )
                ), PageRequest.of(0, 3)).getContent();

        if (products.isEmpty()) {
            products = productRepository.findAll((root, query, cb) ->
                    cb.equal(root.get("status"), ProductStatus.ACTIVE), PageRequest.of(0, 3)).getContent();
        }

        for (Product p : products) {
            suggestedProducts.add(ProductMapper.toCardResponse(p));
        }

        // Gọi Gemini AI nếu có key
        String systemInstruction = "Bạn là Trợ lý AI Chuyên gia Tư vấn Đặc sản Ba Miền của sàn VietSpecialty. " +
                "Nhiệm vụ của bạn là tư vấn ẩm thực quê hương thân thiện, hào hứng, tự hào về nông sản OCOP Việt Nam. " +
                "Hãy trả lời súc tích trong 2-3 đoạn ngắn, nêu bật hương vị đặc trưng, xuất xứ làng nghề và cách thưởng thức.";

        String context = "Dưới đây là các đặc sản hiện có trên sàn:\n" +
                products.stream().map(p -> "- " + p.getName() + " (" + (p.getProvince() != null ? p.getProvince().getName() : "") + "): " + MoneyUtils.formatVnd(p.getPrice()))
                        .collect(Collectors.joining("\n"));

        String userPrompt = "Khách hàng hỏi: \"" + msg + "\"\nNgữ cảnh kho hàng:\n" + context + "\nHãy tư vấn nhiệt tình cho khách.";

        String aiReply = geminiClient.generateContent(systemInstruction, userPrompt);
        if (aiReply != null && !aiReply.isBlank()) {
            return aiReply;
        }

        // Fallback generator thông minh nếu không có mạng/key
        return "Dạ, VietSpecialty xin giới thiệu đến bạn những thức quà đặc sản chuẩn vị truyền thống từ các làng nghề nổi tiếng! " +
                "Các sản phẩm đều đạt tiêu chuẩn OCOP, được kiểm định nghiêm ngặt về vệ sinh an toàn thực phẩm và giữ trọn hương vị nguyên bản đất Việt. " +
                "Bạn có thể bấm vào thẻ sản phẩm bên dưới để xem chi tiết câu chuyện làng nghề và ưu đãi nhé!";
    }

    private String handleGeneralChat(String msg, List<ProductCardResponse> suggestedProducts) {
        String lower = msg.toLowerCase();
        if (lower.contains("chào") || lower.contains("hi") || lower.contains("hello")) {
            return "Dạ chào bạn! Mình là Trợ lý AI của VietSpecialty 🌾. Rất vui được hỗ trợ bạn tìm kiếm đặc sản ba miền, gợi ý quà biếu Tết, hoặc tra cứu hành trình đơn hàng. Hôm nay bạn đang muốn tìm món ngon của vùng đất nào ạ?";
        }

        if (lower.contains("cảm ơn") || lower.contains("thanks")) {
            return "Dạ không có gì ạ! Chúc bạn và gia đình có những bữa ăn ngon miệng và trải nghiệm tuyệt vời cùng đặc sản quê hương VietSpecialty. Nếu cần hỗ trợ thêm, bạn cứ nhắn cho mình nhé!";
        }

        return "Dạ, VietSpecialty luôn sẵn sàng đồng hành cùng bạn! Bạn có thể hỏi mình về nguồn gốc xuất xứ các món đặc sản, cách bảo quản, hoặc gửi mã đơn hàng để mình tra cứu tình trạng vận chuyển tức thì nhé!";
    }

    private String extractKeyword(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("trà") || lower.contains("chè")) return "trà";
        if (lower.contains("mật ong")) return "mật ong";
        if (lower.contains("nước mắm")) return "nước mắm";
        if (lower.contains("tỏi")) return "tỏi";
        if (lower.contains("bánh") || lower.contains("kẹo")) return "bánh";
        if (lower.contains("nem")) return "nem";
        if (lower.contains("gạo")) return "gạo";
        if (lower.contains("hạt")) return "hạt";
        return "đặc sản";
    }
}
