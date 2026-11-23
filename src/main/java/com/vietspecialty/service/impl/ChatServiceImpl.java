package com.vietspecialty.service.impl;

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
import com.vietspecialty.service.ai.VietSpecialtyRagEngine;
import com.vietspecialty.util.MoneyUtils;
import com.vietspecialty.util.VietnameseTextUtils;
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

    private final GeminiClient geminiClient;
    private final VietSpecialtyRagEngine ragEngine;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final VoucherRepository voucherRepository;
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

        if (user != null && conversation.getUser() == null) {
            conversation.setUser(user);
        }

        // 2. Truy xuất Trí nhớ Hội thoại Đa lượt (10 tin nhắn gần nhất, sắp xếp chuẩn theo ID giảm dần, bọc try/catch an toàn)
        List<ChatMessage> recentMessages = new ArrayList<>();
        try {
            recentMessages.addAll(chatMessageRepository.findRecentMessages(
                    conversation.getId(), PageRequest.of(0, 10)
            ));
            recentMessages.sort(Comparator.comparing(ChatMessage::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        } catch (Exception e) {
            log.warn("Không thể đọc lịch sử tin nhắn cũ cho conversationId={} (chatKey={}): {}",
                    conversation.getId(), chatKey, e.getMessage());
        }

        // 3. Tải dữ liệu thực tế từ Database (Multi-Source RAG: Products + Vouchers)
        List<Product> allActiveProducts = loadAllActiveProductsInitialized();
        List<Voucher> activeVouchers = loadAllActiveVouchers();

        // 4. Trích xuất Active Product Context từ lượt BOT gợi ý gần nhất
        List<Product> activeContextProducts = extractActiveProductContext(recentMessages, allActiveProducts);
        List<Map<String, String>> historyTurns = buildChronologicalHistoryTurns(recentMessages);

        List<Product> suggestedEntityProducts = new ArrayList<>();
        ChatIntent intent;
        String groundedReply;

        // 5. KIỂM TRA HÀNG RÀO KHUÔN KHỔ WEBSITE (OUT-OF-SCOPE GUARDRAIL)
        List<VietSpecialtyRagEngine.OutOfScopeCategory> outOfScopeCategories = ragEngine.detectOutOfScopeCategories(msg);
        if (!outOfScopeCategories.isEmpty()) {
            intent = ChatIntent.OTHER;
            groundedReply = ragEngine.buildOutOfScopeResponse(
                    msg, outOfScopeCategories, allActiveProducts, activeVouchers, suggestedEntityProducts
            );
        } else if (isOrderLookupIntent(msg)) {
            // 6. TRA CỨU ĐƠN HÀNG & KHÓA BẢO MẬT ĐƠN HÀNG (PRIVACY GUARDRAIL)
            intent = ChatIntent.ORDER_LOOKUP;
            groundedReply = handleOrderLookup(msg, user);
        } else {
            // 7. SUY LUẬN HỘI THOẠI ĐA LƯỢT & TƯ VẤN ĐẶC SẢN / VOUCHER / CHÍNH SÁCH ĐỘNG
            List<Product> explicitProducts = ragEngine.findExplicitlyMentionedProducts(msg, allActiveProducts);
            boolean hasAnaphora = ragEngine.hasAnaphoricProductReference(msg);

            // Xác định sản phẩm mục tiêu cho câu hỏi nối tiếp (ưu tiên đại từ chỉ định "món này"/"2 hộp này" hoặc món vừa nhắc)
            List<Product> targetProducts;
            if (hasAnaphora && !activeContextProducts.isEmpty()) {
                targetProducts = activeContextProducts;
            } else if (!explicitProducts.isEmpty()) {
                targetProducts = explicitProducts;
            } else {
                targetProducts = activeContextProducts;
            }

            if (ragEngine.isCalculationQuestion(msg) && !targetProducts.isEmpty()) {
                // Lượt hỏi tính toán: "Nếu mình lấy 2 hộp này thì tổng bao nhiêu tiền và phí ship tính sao?"
                intent = ChatIntent.PRODUCT_ADVICE;
                Product target = targetProducts.get(0);
                suggestedEntityProducts.add(target);
                groundedReply = ragEngine.synthesizeQuantityAndShippingCalculation(msg, target, activeVouchers);
            } else if (ragEngine.isProductAttributeDeepDiveQuestion(msg) && !targetProducts.isEmpty()) {
                // Lượt hỏi sâu thuộc tính: "Món này bảo quản thế nào và hạn sử dụng bao lâu?"
                intent = ChatIntent.PRODUCT_ADVICE;
                suggestedEntityProducts.addAll(targetProducts);
                groundedReply = ragEngine.synthesizeProductAttributeDeepDive(msg, targetProducts, activeVouchers);
            } else if (ragEngine.isComparisonQuestion(msg) && (!targetProducts.isEmpty() || !allActiveProducts.isEmpty())) {
                // Lượt hỏi so sánh các món vừa gợi ý
                intent = ChatIntent.PRODUCT_ADVICE;
                List<Product> toCompare = targetProducts.size() >= 2
                        ? targetProducts
                        : ragEngine.retrieveProductsMultiCriteria(msg, allActiveProducts, new VietSpecialtyRagEngine.BudgetFilter(null, null), 3);
                suggestedEntityProducts.addAll(toCompare);
                groundedReply = ragEngine.synthesizeProductComparison(toCompare);
            } else if (ragEngine.isCheaperAlternativeQuestion(msg)) {
                // Lượt hỏi tìm món rẻ hơn
                intent = ChatIntent.PRODUCT_ADVICE;
                Product ref = !targetProducts.isEmpty() ? targetProducts.get(0) : null;
                groundedReply = ragEngine.synthesizeCheaperAlternatives(ref, allActiveProducts, suggestedEntityProducts);
            } else if (ragEngine.isVoucherInquiry(msg) && !isStrictPolicyOnlyQuestion(msg)) {
                // Hỏi về mã giảm giá / voucher thực tế đang có trên sàn
                intent = isPolicyDominantQuestion(msg) ? ChatIntent.POLICY : ChatIntent.PRODUCT_ADVICE;
                groundedReply = ragEngine.synthesizeActiveVouchersResponse(
                        msg, activeVouchers, activeContextProducts, allActiveProducts, suggestedEntityProducts
                );
            } else if (isPolicyDominantQuestion(msg)) {
                // Hỏi về chính sách phí vận chuyển, đổi trả 48h, hoàn tiền, thanh toán VNPay/COD (Tổng hợp động theo ngữ cảnh)
                intent = ChatIntent.POLICY;
                groundedReply = ragEngine.synthesizeDynamicPolicyResponse(msg, activeContextProducts, activeVouchers);
            } else if (isProductAdviceIntent(msg, explicitProducts)) {
                intent = ChatIntent.PRODUCT_ADVICE;
                Optional<String> unavailableOpt = ragEngine.detectUnavailableSpecialtyRequest(msg, explicitProducts);
                if (unavailableOpt.isPresent()) {
                    // Khóa Dữ liệu Thực tế: Trung thực báo món chưa mở bán & gợi ý đặc sản tương đương
                    groundedReply = ragEngine.synthesizeUnavailableSpecialtyResponse(
                            unavailableOpt.get(), msg, allActiveProducts, suggestedEntityProducts
                    );
                } else {
                    VietSpecialtyRagEngine.BudgetFilter budgetFilter = ragEngine.extractBudgetFilter(msg);
                    List<Product> retrieved = ragEngine.retrieveProductsMultiCriteria(msg, allActiveProducts, budgetFilter, 3);
                    suggestedEntityProducts.addAll(retrieved);
                    groundedReply = ragEngine.synthesizeContextualProductAdvice(msg, retrieved, budgetFilter, activeVouchers);
                }
            } else {
                // Giao tiếp tự nhiên gắn liền với dữ liệu sống của sàn VietSpecialty
                intent = ChatIntent.OTHER;
                groundedReply = synthesizeDynamicGeneralConversation(
                        msg, activeContextProducts, allActiveProducts, activeVouchers, suggestedEntityProducts
                );
            }
        }

        // 8. Nếu có Google AI Studio API Key hợp lệ (AIza...), kết hợp Gemini Multi-turn LLM với Grounded Context
        String finalReply = tryEnhanceWithGeminiMultiTurn(
                msg, historyTurns, activeContextProducts, suggestedEntityProducts, activeVouchers, groundedReply
        );

        List<ProductCardResponse> suggestedProducts = suggestedEntityProducts.stream()
                .distinct()
                .map(ProductMapper::toCardResponse)
                .toList();

        List<String> suggestedQuestions = ragEngine.buildDynamicFollowUpQuestions(
                intent, msg, suggestedEntityProducts, activeContextProducts, activeVouchers
        );

        // 9. Lưu tin nhắn người dùng và bot vào DB (flush ngay để các lượt hội thoại liên tiếp trong cùng transaction đọc được tức thì)
        ChatMessage userMsg = ChatMessage.builder()
                .conversation(conversation)
                .role(ChatRole.USER)
                .content(msg)
                .intent(intent)
                .build();
        chatMessageRepository.saveAndFlush(userMsg);

        // Nếu lượt này không gợi ý sản phẩm mới nhưng có activeContextProducts, giữ tiếp mạch sản phẩm cho lượt sau
        List<Product> productsToPersistInMemory = !suggestedEntityProducts.isEmpty()
                ? suggestedEntityProducts
                : activeContextProducts;

        String productIdsStr = productsToPersistInMemory.stream()
                .map(p -> String.valueOf(p.getId()))
                .distinct()
                .collect(Collectors.joining(","));

        ChatMessage botMsg = ChatMessage.builder()
                .conversation(conversation)
                .role(ChatRole.BOT)
                .content(finalReply)
                .intent(intent)
                .suggestedProductIds(productIdsStr)
                .build();
        chatMessageRepository.saveAndFlush(botMsg);

        conversation.setLastMessageAt(LocalDateTime.now());
        chatConversationRepository.saveAndFlush(conversation);

        return new ChatResponse(finalReply, intent, chatKey, suggestedProducts, suggestedQuestions);
    }

    private String tryEnhanceWithGeminiMultiTurn(
            String currentMessage,
            List<Map<String, String>> historyTurns,
            List<Product> activeContextProducts,
            List<Product> suggestedEntityProducts,
            List<Voucher> activeVouchers,
            String groundedReply
    ) {
        String systemPrompt = ragEngine.buildGroundedSystemPrompt(
                activeContextProducts, suggestedEntityProducts, activeVouchers
        );
        String userPromptWithFacts = "Tin nhắn khách hàng: \"" + currentMessage + "\"\n\n" +
                "[DỮ LIỆU ĐÃ KIỂM CHỨNG TỪ DATABASE VIETSPECIALTY ĐỂ BẠN TRẢ LỜI CHÍNH XÁC]:\n" +
                groundedReply + "\n\n" +
                "Hãy trả lời khách hàng thật tự nhiên, thân thiện và giữ nguyên chính xác 100% tên sản phẩm, con số giá tiền, trọng lượng, hạn dùng, cách bảo quản, mã voucher trong dữ liệu trên.";

        String llmReply = geminiClient.generateMultiTurnContent(systemPrompt, historyTurns, userPromptWithFacts);
        if (llmReply != null && !llmReply.isBlank()) {
            return llmReply;
        }
        return groundedReply;
    }

    private List<Product> loadAllActiveProductsInitialized() {
        List<Product> products = productRepository.findAll((root, query, cb) ->
                cb.equal(root.get("status"), ProductStatus.ACTIVE));
        // Khởi tạo an toàn các quan hệ LAZY trong Transaction
        for (Product p : products) {
            if (p.getShop() != null) p.getShop().getName();
            if (p.getProvince() != null) {
                p.getProvince().getName();
                if (p.getProvince().getRegion() != null) {
                    p.getProvince().getRegion().getName();
                }
            }
            if (p.getCategory() != null) p.getCategory().getName();
            if (p.getImages() != null) p.getImages().size();
        }
        return products;
    }

    private List<Voucher> loadAllActiveVouchers() {
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> vouchers = voucherRepository.findAll().stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .filter(v -> v.getEndAt() == null || v.getEndAt().isAfter(now))
                .filter(v -> v.getUsageLimit() == null || v.getUsedCount() < v.getUsageLimit())
                .toList();
        for (Voucher v : vouchers) {
            if (v.getShop() != null) {
                v.getShop().getName();
            }
        }
        return vouchers;
    }

    private List<Product> extractActiveProductContext(List<ChatMessage> recentMessages, List<Product> allActiveProducts) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productMap = allActiveProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        for (ChatMessage m : recentMessages) {
            if (m.getRole() == ChatRole.BOT && m.getSuggestedProductIds() != null && !m.getSuggestedProductIds().isBlank()) {
                List<Product> context = new ArrayList<>();
                for (String rawId : m.getSuggestedProductIds().split(",")) {
                    try {
                        Long pid = Long.parseLong(rawId.trim());
                        Product found = productMap.get(pid);
                        if (found != null && !context.contains(found)) {
                            context.add(found);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (!context.isEmpty()) {
                    return context;
                }
            }
        }
        return List.of();
    }

    private List<Map<String, String>> buildChronologicalHistoryTurns(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> chronological = new ArrayList<>(recentMessages);
        Collections.reverse(chronological);

        List<Map<String, String>> turns = new ArrayList<>();
        for (ChatMessage m : chronological) {
            String role = m.getRole() == ChatRole.BOT ? "model" : "user";
            turns.add(Map.of(
                    "role", role,
                    "content", m.getContent() != null ? m.getContent() : ""
            ));
        }
        return turns;
    }

    private boolean isOrderLookupIntent(String msg) {
        if (ORDER_CODE_PATTERN.matcher(msg).find()) {
            return true;
        }
        String norm = VietnameseTextUtils.normalizeSearchText(msg);
        if (norm.contains("tra cuu") || norm.contains("ma don") || norm.contains("kien hang")
                || norm.contains("van don") || norm.contains("don hang cua toi")
                || norm.contains("don cua toi") || norm.contains("kiem tra don")) {
            return true;
        }
        // Nếu chứa "đơn hàng" nhưng không phải hỏi phí ship / freeship / voucher cho đơn hàng
        if (norm.contains("don hang") && !norm.contains("phi ship") && !norm.contains("van chuyen")
                && !norm.contains("freeship") && !norm.contains("mien phi") && !norm.contains("voucher")
                && !norm.contains("ma giam gia") && !norm.contains("toi thieu")) {
            return true;
        }
        return false;
    }

    private boolean isStrictPolicyOnlyQuestion(String msg) {
        String norm = VietnameseTextUtils.normalizeSearchText(msg);
        return (norm.contains("doi tra") || norm.contains("hoan tien") || norm.contains("chinh sach"))
                && !norm.contains("voucher") && !norm.contains("ma giam gia");
    }

    private boolean isPolicyDominantQuestion(String msg) {
        String norm = VietnameseTextUtils.normalizeSearchText(msg);
        return norm.contains("van chuyen") || norm.contains("phi ship") || norm.contains("bao lau nhan duoc")
                || norm.contains("doi tra") || norm.contains("hoan tien") || norm.contains("chinh sach")
                || norm.contains("thanh toan") || norm.contains("vnpay") || norm.contains("cod")
                || norm.contains("freeship");
    }

    private boolean isProductAdviceIntent(String msg, List<Product> explicitProducts) {
        if (!explicitProducts.isEmpty()) {
            return true;
        }
        VietSpecialtyRagEngine.BudgetFilter budget = ragEngine.extractBudgetFilter(msg);
        if (budget.hasConstraint()) {
            return true;
        }
        String norm = VietnameseTextUtils.normalizeSearchText(msg);
        String[] keywords = {
                "dac san", "qua", "bieu", "tang", "tet", "sep", "mon nham", "moi nham", "nham", "nhau",
                "uong bia", "lai rai", "mam com", "bua com", "gia dinh", "an vat", "thuong tra",
                "che", "tra", "mat ong", "nuoc mam", "banh", "keo", "nem", "hat", "thit trau",
                "gac bep", "lap xuong", "yen sao", "toi", "tom", "ca", "ruou", "ca phe", "mien",
                "gao", "com", "me xung", "tieu", "muoi", "xoai", "buoi", "mac ca", "ocop",
                "tu van", "goi y", "mien bac", "tay bac", "mien trung", "tay nguyen", "mien nam",
                "mien tay", "thai nguyen", "hue", "ca mau", "son la", "ly son", "khanh hoa",
                "lam dong", "da lat", "ben tre", "soc trang", "dong thap", "phu quoc", "ngon",
                "gia bao nhieu", "mua", "ban gi", "combo", "pho", "bun", "sau rieng",
                "suc khoe", "tot cho", "boi bo", "de khang", "ngu ngon", "me bau", "ba bau",
                "nguoi gia", "nguoi lon tuoi", "tre em", "tieu duong", "an kieng", "giam can",
                "da day", "huyet ap", "dep da", "thanh nhiet", "am bung", "mon gi", "an gi",
                "uong gi", "an voi", "an kem", "cham voi", "cham gi", "rau muong", "rau luoc",
                "thit luoc", "com nong", "chao", "do chua", "do ngot", "do cay", "do kho",
                "khai vi", "trang mieng", "sinh nhat", "hop mat", "tiec", "bbq", "nuong", "lau"
        };
        for (String kw : keywords) {
            if (norm.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String handleOrderLookup(String msg, User user) {
        Matcher m = ORDER_CODE_PATTERN.matcher(msg);
        if (m.find()) {
            String code = m.group().toUpperCase();
            Optional<Order> orderOpt = orderRepository.findByCode(code);
            if (orderOpt.isPresent()) {
                Order o = orderOpt.get();
                if (o.getShop() != null) o.getShop().getName();
                if (o.getUser() != null) o.getUser().getId();
                List<OrderItem> items = orderItemRepository.findByOrderId(o.getId());
                return ragEngine.buildPrivacyAwareOrderResponse(o, user, items);
            } else {
                return "Dạ, VietSpecialty đã kiểm tra trên hệ thống nhưng không tìm thấy đơn hàng nào có mã **\"" + code + "\"**. " +
                        "Bạn vui lòng kiểm tra lại chính xác mã đơn (bắt đầu bằng `VS...`, ví dụ: `VS261111XXXXX`) trong email xác nhận hoặc tại mục **\"Đơn hàng của tôi\"** nhé!";
            }
        }

        if (user != null) {
            var recentOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 3));
            if (!recentOrders.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Dạ chào **").append(user.getFullName()).append("**, mình tìm thấy **")
                        .append(recentOrders.getContent().size()).append(" đơn hàng gần nhất** trên tài khoản của bạn:\n\n");
                for (Order o : recentOrders.getContent()) {
                    String shopName = o.getShop() != null ? o.getShop().getName() : "Gian hàng HTX";
                    sb.append("● Mã đơn **#").append(o.getCode()).append("** (").append(shopName).append(") — Trạng thái: **")
                            .append(o.getStatus().getDescription()).append("** | Tổng thanh toán: **")
                            .append(MoneyUtils.formatVnd(o.getTotalAmount())).append("**\n");
                }
                sb.append("\nBạn hãy nhắn mã đơn cụ thể (ví dụ: `#").append(recentOrders.getContent().get(0).getCode())
                        .append("`) để mình liệt kê chi tiết từng món trong đơn nhé!");
                return sb.toString();
            }
        }

        return "Dạ, để mình tra cứu chính xác hành trình kiện hàng và danh sách đặc sản đã đặt, bạn vui lòng nhắn cho mình **mã đơn hàng** (bắt đầu bằng `VS...`, ví dụ: `VS261111ABCDE`) hoặc đăng nhập vào tài khoản mua hàng nhé!";
    }

    private String synthesizeDynamicGeneralConversation(
            String msg,
            List<Product> activeContextProducts,
            List<Product> allProducts,
            List<Voucher> activeVouchers,
            List<Product> outSuggestedProducts
    ) {
        String norm = VietnameseTextUtils.normalizeSearchText(msg);

        if (norm.contains("cam on") || norm.contains("thank")) {
            if (!activeContextProducts.isEmpty()) {
                Product p = activeContextProducts.get(0);
                return "Dạ rất vui được đồng hành cùng bạn! Nếu bạn cần đặt mua món **" + p.getName() + "** (**" +
                        MoneyUtils.formatVnd(p.getPrice()) + "**) hoặc cần hỗ trợ áp mã giảm giá lúc thanh toán, bạn cứ nhắn mình bất cứ lúc nào nhé! Chúc bạn và gia đình thật nhiều sức khỏe! 🌾";
            }
            return "Dạ không có gì ạ! Rất hân hạnh được đồng hành cùng bạn khám phá đặc sản 3 miền trên **VietSpecialty**. Chúc bạn và gia đình có những bữa thưởng thức ẩm thực quê hương thật ấm cúng nhé! 🌾";
        }

        // Khi khách gõ ký tự ngắn như "..", "?", "alo" -> trả lời ngắn gọn, thân thiện, tự nhiên
        if (norm.isBlank() || norm.length() <= 3 || norm.equals("alo")) {
            if (!activeContextProducts.isEmpty()) {
                Product p = activeContextProducts.get(0);
                outSuggestedProducts.addAll(activeContextProducts.stream().limit(2).toList());
                return "Dạ mình vẫn đang ở đây để hỗ trợ bạn ạ! 😊 Bạn có muốn hỏi thêm về món **" + p.getName() + "** (**" +
                        MoneyUtils.formatVnd(p.getPrice()) + "**) hay muốn mình gợi ý thêm đặc sản vùng miền nào khác không?";
            }
            return "Dạ chào bạn! Mình là Trợ lý AI của **VietSpecialty** đây ạ 😊. Bạn đang muốn tìm đặc sản bồi bổ sức khỏe, món ăn kèm mâm cơm gia đình, mồi nhắm hay quà biếu OCOP để mình tư vấn ngay nhé?";
        }

        List<Product> featured = allProducts.stream()
                .filter(p -> p.getOcopRating() != null && p.getOcopRating() == 5)
                .limit(3)
                .toList();
        outSuggestedProducts.addAll(featured);

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ xin chào bạn! Mình là **Trợ lý AI Tư vấn Ẩm thực & Đặc sản Vùng miền của sàn VietSpecialty** 🌾.\n\n")
                .append("Hiện tại trên sàn đang quy tụ **").append(allProducts.size())
                .append(" đặc sản OCOP 3-5 sao** chính gốc từ các Hợp tác xã Bắc - Trung - Nam, kèm **")
                .append(activeVouchers.size()).append(" mã Voucher ưu đãi** đang mở (nổi bật như mã **`VIETSPECIALTY`** giảm 50.000₫, **`FREESHIP30`** hỗ trợ 30.000₫ phí ship).\n\n")
                .append("Mình có thể hỗ trợ bạn ngay:\n")
                .append("● **Tư vấn chọn đặc sản** theo ngân sách (*dưới 300k, 400k, 500k*), theo dịp (*mồi nhắm uống bia, quà biếu sếp/phụ huynh, mâm cơm gia đình*).\n")
                .append("● **Giải đáp sâu từng món:** cách bảo quản, hạn sử dụng, trọng lượng, câu chuyện làng nghề và tính nhẩm tổng tiền + phí ship.\n")
                .append("● **Tra cứu đơn hàng** theo mã đơn `VS...`.\n\n")
                .append("Hôm nay bạn đang muốn tìm thức quà đặc sản của vùng đất nào ạ?");
        return sb.toString();
    }
}
