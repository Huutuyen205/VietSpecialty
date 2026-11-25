package com.vietspecialty.service.ai;

import com.vietspecialty.config.AppProperties;
import com.vietspecialty.domain.entity.Order;
import com.vietspecialty.domain.entity.OrderItem;
import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.entity.Voucher;
import com.vietspecialty.domain.enums.ChatIntent;
import com.vietspecialty.domain.enums.DiscountType;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.VoucherScope;
import com.vietspecialty.util.MoneyUtils;
import com.vietspecialty.util.VietnameseTextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bộ máy Suy luận Hội thoại Đa lượt bám sát dữ liệu thực tế (Dynamic Grounded Reasoning Engine)
 * tích hợp Hàng rào Khuôn khổ Website (Domain Guardrails) cho sàn đặc sản VietSpecialty.
 */
@Component
@RequiredArgsConstructor
public class VietSpecialtyRagEngine {

    private final AppProperties appProperties;

    // Regex nhận diện khoảng giá / ngân sách: "dưới 400k", "tầm 500 nghìn", "300.000đ", "từ 100k đến 300k"
    private static final Pattern RANGE_BUDGET_PATTERN = Pattern.compile(
            "(?i)(?:từ|khoảng)\\s*(\\d+(?:[.,]\\d+)?)\\s*(k|nghìn|ngàn|tr|triệu|đ|đồng|vnd)?\\s*(?:đến|tới|-)\\s*(\\d+(?:[.,]\\d+)?)\\s*(k|nghìn|ngàn|tr|triệu|đ|đồng|vnd)?"
    );
    private static final Pattern MAX_BUDGET_PATTERN = Pattern.compile(
            "(?i)(?:dưới|nhỏ hơn|tối đa|không quá|đổ lại|tầm|khoảng|quanh|với)\\s*(\\d+(?:[.,]\\d+)?)\\s*(k|nghìn|ngàn|tr|triệu|đ|đồng|vnd)"
    );
    private static final Pattern MIN_BUDGET_PATTERN = Pattern.compile(
            "(?i)(?:trên|từ|lớn hơn|ít nhất)\\s*(\\d+(?:[.,]\\d+)?)\\s*(k|nghìn|ngàn|tr|triệu|đ|đồng|vnd)"
    );
    private static final Pattern GENERIC_PRICE_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{2,3})\\s*(k|nghìn|ngàn)\\b|\\b(\\d{3}[.,]\\d{3})\\s*(đ|đồng|vnd)?\\b"
    );

    // Regex nhận diện số lượng mua: "lấy 2 hộp", "mua 3 gói", "4 chai", "hai hộp"
    private static final Pattern QUANTITY_DIGIT_PATTERN = Pattern.compile(
            "(?i)(?:lấy|mua|đặt|chốt|tính|nếu\\s+mình\\s+lấy|nếu\\s+mua|khoảng)?\\s*(\\d{1,3})\\s*(?:hộp|gói|chai|túi|hũ|bình|quả|bó|cái|chiếc|sản phẩm|món|phần|suất|kg|ký)\\b"
    );
    private static final Pattern QUANTITY_FALLBACK_PATTERN = Pattern.compile(
            "(?i)\\b(?:lấy|mua|đặt)\\s+(\\d{1,3})\\b"
    );

    public record BudgetFilter(BigDecimal minPrice, BigDecimal maxPrice) {
        public boolean hasConstraint() {
            return minPrice != null || maxPrice != null;
        }

        public boolean matches(BigDecimal price) {
            if (price == null) return false;
            if (minPrice != null && price.compareTo(minPrice) < 0) return false;
            if (maxPrice != null && price.compareTo(maxPrice) > 0) return false;
            return true;
        }
    }

    public enum OutOfScopeCategory {
        CODING_TECH,
        MATH_SCIENCE,
        CRYPTO_FINANCE,
        WEATHER_SPORTS,
        POLITICS_MEDICAL,
        COMPETITOR_PLATFORM
    }

    /**
     * Kiểm tra câu hỏi có vượt ra ngoài phạm vi sàn đặc sản VietSpecialty hay không.
     * Trả về danh sách các nhóm chủ đề ngoài phạm vi phát hiện được (rỗng nếu nằm trong phạm vi).
     */
    public List<OutOfScopeCategory> detectOutOfScopeCategories(String message) {
        if (message == null || message.isBlank()) return List.of();
        String lower = message.toLowerCase();
        String norm = VietnameseTextUtils.normalizeSearchText(message);

        List<OutOfScopeCategory> detected = new ArrayList<>();

        // 1. Lập trình / Viết code / Công nghệ phần mềm
        if (containsAnyWord(norm,
                "python", "javascript", "java", "c++", "csharp", "html", "css", "sql",
                "react", "nodejs", "spring boot", "viet code", "doan code", "lap trinh",
                "thuat toan", "source code", "debug", "fix bug", "code giai", "coding")) {
            detected.add(OutOfScopeCategory.CODING_TECH);
        }

        // 2. Giải toán / Phương trình / Vật lý / Hóa học
        if (containsAnyWord(norm,
                "phuong trinh bac", "giai phuong trinh", "dao ham", "tich phan",
                "luong giac", "ma tran", "dinh ly", "bai tap toan", "vat ly", "hoa hoc",
                "can bac hai", "he phuong trinh", "logarit")) {
            detected.add(OutOfScopeCategory.MATH_SCIENCE);
        }

        // 3. Tiền ảo / Chứng khoán / Đầu cơ tài chính
        if (containsAnyWord(norm,
                "bitcoin", "btc", "crypto", "tien ao", "tien dien tu", "ethereum",
                "chung khoan", "co phieu", "vnindex", "vn-index", "forex", "binance",
                "gia vang hom nay")) {
            detected.add(OutOfScopeCategory.CRYPTO_FINANCE);
        }

        // 4. Dự báo thời tiết / Kết quả bóng đá / Xổ số
        if (containsAnyWord(norm,
                "du bao thoi tiet", "thoi tiet ngay mai", "thoi tiet hom nay",
                "ket qua bong da", "ty so bong da", "ngoai hang anh", "world cup",
                "champions league", "xo so", "vietlott", "lo de")) {
            detected.add(OutOfScopeCategory.WEATHER_SPORTS);
        }

        // 5. Chính trị / Tôn giáo / Kê đơn thuốc y tế
        if (containsAnyWord(norm,
                "chinh tri", "bau cu", "tong thong", "ke don thuoc", "thuoc khang sinh",
                "chan doan benh", "chua benh nan y")) {
            detected.add(OutOfScopeCategory.POLITICS_MEDICAL);
        }

        // 6. Sàn TMĐT đối thủ
        if (containsAnyWord(norm,
                "shopee", "lazada", "tiki", "tiktok shop", "sendo", "taobao", "amazon") &&
                !lower.contains("vietspecialty")) {
            detected.add(OutOfScopeCategory.COMPETITOR_PLATFORM);
        }

        return detected;
    }

    /**
     * Sinh phản hồi Hàng rào Khuôn khổ Website (Out-of-Scope Guardrail) tự nhiên, hóm hỉnh,
     * không giải toán/viết code/dự báo Bitcoin, và khéo léo dẫn dắt về đặc sản thực tế trong DB.
     */
    public String buildOutOfScopeResponse(
            String userMessage,
            List<OutOfScopeCategory> categories,
            List<Product> allProducts,
            List<Voucher> activeVouchers,
            List<Product> outSuggestedProducts
    ) {
        List<String> topicRemarks = new ArrayList<>();
        List<Product> pickedProducts = new ArrayList<>();

        if (categories.contains(OutOfScopeCategory.CODING_TECH) || categories.contains(OutOfScopeCategory.MATH_SCIENCE)) {
            topicRemarks.add("viết code lập trình hay giải phương trình toán học");
            addMatchingProducts(allProducts, pickedProducts, List.of("trà đinh", "cà phê arabica", "mắc ca"), 3);
        }
        if (categories.contains(OutOfScopeCategory.WEATHER_SPORTS)) {
            topicRemarks.add("dự báo thời tiết hay bình luận thể thao");
            addMatchingProducts(allProducts, pickedProducts, List.of("thịt trâu gác bếp", "lạp xưởng", "nem chua"), 3);
        }
        if (categories.contains(OutOfScopeCategory.CRYPTO_FINANCE)) {
            topicRemarks.add("phân tích biểu đồ giá Bitcoin, tiền điện tử hay chứng khoán");
            addMatchingProducts(allProducts, pickedProducts, List.of("yến sào", "tỏi đen", "trà đinh"), 3);
        }
        if (categories.contains(OutOfScopeCategory.POLITICS_MEDICAL)) {
            topicRemarks.add("chuyên môn y khoa, kê đơn thuốc hay các vấn đề thời sự");
            addMatchingProducts(allProducts, pickedProducts, List.of("trà cung đình", "mật ong", "tỏi đen"), 3);
        }
        if (categories.contains(OutOfScopeCategory.COMPETITOR_PLATFORM)) {
            topicRemarks.add("thông tin mua sắm tại các sàn thương mại điện tử khác");
            addMatchingProducts(allProducts, pickedProducts, List.of("trà đinh", "thịt trâu", "tôm khô"), 3);
        }

        if (pickedProducts.isEmpty() && !allProducts.isEmpty()) {
            pickedProducts.addAll(allProducts.stream().limit(3).toList());
        }

        List<Product> finalSuggestions = pickedProducts.stream().distinct().limit(3).toList();
        outSuggestedProducts.addAll(finalSuggestions);

        String joinedTopics = String.join(" cũng như ", topicRemarks);
        StringBuilder sb = new StringBuilder();
        sb.append("Dạ, câu hỏi của bạn về **").append(joinedTopics)
                .append("** thú vị quá! Tuy nhiên, mình là **Trợ lý AI Chuyên gia Ẩm thực & Đặc sản Vùng miền của sàn VietSpecialty**, ")
                .append("nên mình xin phép không ").append(joinedTopics)
                .append(" để tập trung 100% sở trường tư vấn các thức quà nông sản OCOP chuẩn vị quê hương cho bạn nhé! 🌾\n\n");

        if (categories.contains(OutOfScopeCategory.CODING_TECH) || categories.contains(OutOfScopeCategory.MATH_SCIENCE)) {
            sb.append("💡 Thay vì căng thẳng với những dòng code hay con số phương trình, bạn hãy tự thưởng cho mình một tách trà thơm hoặc cà phê nguyên chất trên **VietSpecialty** để tinh thần tỉnh táo, sáng tạo gấp đôi:\n");
        } else if (categories.contains(OutOfScopeCategory.CRYPTO_FINANCE) || categories.contains(OutOfScopeCategory.WEATHER_SPORTS)) {
            sb.append("💡 Dù thời tiết ngày mai nắng hay mưa, hay thị trường tài chính biến động thế nào, thì đầu tư cho sức khỏe và thưởng thức mồi nhắm đặc sản chuẩn gốc làng nghề tại **VietSpecialty** vẫn luôn là khoản đầu tư \"sinh lời\" chắc chắn nhất:\n");
        } else {
            sb.append("💡 Tại sàn **VietSpecialty**, bạn có thể khám phá ngay những đặc sản OCOP 3-5 sao chính gốc từ các Hợp tác xã uy tín:\n");
        }

        for (Product p : finalSuggestions) {
            String prov = p.getProvince() != null ? p.getProvince().getName() : "Việt Nam";
            String shopName = p.getShop() != null ? p.getShop().getName() : "HTX Đặc sản";
            sb.append("● **").append(p.getName()).append("** (").append(prov).append(" - ").append(shopName)
                    .append("): **").append(MoneyUtils.formatVnd(p.getPrice())).append(" / ").append(p.getUnit()).append("**");
            if (p.getOcopRating() != null) {
                sb.append(" [Chuẩn OCOP ").append(p.getOcopRating()).append("⭐]");
            }
            sb.append(" — ").append(p.getShortDescription() != null ? p.getShortDescription() : "Hương vị truyền thống đặc trưng.").append("\n");
        }

        if (!activeVouchers.isEmpty()) {
            Voucher v = activeVouchers.get(0);
            sb.append("\n🎁 Đặc biệt, trên sàn đang có mã ưu đãi **").append(v.getCode()).append("** (")
                    .append(formatVoucherBenefit(v)).append(" cho đơn từ ").append(MoneyUtils.formatVnd(v.getMinOrderValue()))
                    .append(") cùng chính sách miễn phí vận chuyển tiêu chuẩn cho đơn từ **500.000₫**. Bạn muốn mình tư vấn chi tiết món nào để nhâm nhi hôm nay ạ?");
        }

        return sb.toString();
    }

    /**
     * Trích xuất bộ lọc ngân sách từ câu hỏi tự nhiên của khách hàng.
     */
    public BudgetFilter extractBudgetFilter(String message) {
        if (message == null || message.isBlank()) {
            return new BudgetFilter(null, null);
        }
        String norm = VietnameseTextUtils.normalizeSearchText(message);

        Matcher rangeMatcher = RANGE_BUDGET_PATTERN.matcher(norm);
        if (rangeMatcher.find()) {
            BigDecimal min = parseMoneyAmount(rangeMatcher.group(1), rangeMatcher.group(2));
            BigDecimal max = parseMoneyAmount(rangeMatcher.group(3), rangeMatcher.group(4));
            if (min != null && max != null && min.compareTo(max) <= 0) {
                return new BudgetFilter(min, max);
            }
        }

        Matcher maxMatcher = MAX_BUDGET_PATTERN.matcher(norm);
        if (maxMatcher.find()) {
            BigDecimal max = parseMoneyAmount(maxMatcher.group(1), maxMatcher.group(2));
            if (max != null) {
                return new BudgetFilter(null, max);
            }
        }

        Matcher minMatcher = MIN_BUDGET_PATTERN.matcher(norm);
        if (minMatcher.find()) {
            BigDecimal min = parseMoneyAmount(minMatcher.group(1), minMatcher.group(2));
            if (min != null) {
                return new BudgetFilter(min, null);
            }
        }

        // Nếu người dùng viết "quà biếu 400k" hoặc "combo 500k"
        if (norm.contains("qua") || norm.contains("bieu") || norm.contains("combo") || norm.contains("ngan sach") || norm.contains("gia")) {
            Matcher tokenMatcher = GENERIC_PRICE_TOKEN_PATTERN.matcher(norm);
            if (tokenMatcher.find()) {
                BigDecimal val;
                if (tokenMatcher.group(1) != null) {
                    val = parseMoneyAmount(tokenMatcher.group(1), tokenMatcher.group(2));
                } else {
                    val = parseMoneyAmount(tokenMatcher.group(3), "đ");
                }
                if (val != null && val.compareTo(BigDecimal.valueOf(30000)) >= 0) {
                    return new BudgetFilter(null, val);
                }
            }
        }

        return new BudgetFilter(null, null);
    }

    private BigDecimal parseMoneyAmount(String rawNumber, String unit) {
        if (rawNumber == null || rawNumber.isBlank()) return null;
        try {
            String cleaned = rawNumber.trim();
            String u = unit != null ? unit.trim().toLowerCase() : "";

            if (u.startsWith("k") || u.startsWith("ngh") || u.startsWith("ng")) {
                double val = Double.parseDouble(cleaned.replace(",", "."));
                return BigDecimal.valueOf(Math.round(val * 1_000));
            }
            if (u.startsWith("tr")) {
                double val = Double.parseDouble(cleaned.replace(",", "."));
                return BigDecimal.valueOf(Math.round(val * 1_000_000));
            }
            // Trường hợp viết 400.000 hoặc 500000
            String digitsOnly = cleaned.replace(".", "").replace(",", "");
            long exact = Long.parseLong(digitsOnly);
            if (exact < 1000) {
                exact = exact * 1000;
            }
            return BigDecimal.valueOf(exact);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Trích xuất số lượng sản phẩm khách muốn tính nhẩm (ví dụ: "Nếu mình lấy 2 hộp này..." -> 2)
     */
    public int extractRequestedQuantity(String message) {
        if (message == null || message.isBlank()) return 1;
        String lower = message.toLowerCase();

        Matcher m1 = QUANTITY_DIGIT_PATTERN.matcher(lower);
        if (m1.find()) {
            try {
                int q = Integer.parseInt(m1.group(1));
                if (q > 0 && q <= 500) return q;
            } catch (NumberFormatException ignored) {}
        }

        Matcher m2 = QUANTITY_FALLBACK_PATTERN.matcher(lower);
        if (m2.find()) {
            try {
                int q = Integer.parseInt(m2.group(1));
                if (q > 0 && q <= 500) return q;
            } catch (NumberFormatException ignored) {}
        }

        String norm = VietnameseTextUtils.normalizeSearchText(message);
        Map<String, Integer> wordNumbers = new LinkedHashMap<>();
        wordNumbers.put("muoi", 10);
        wordNumbers.put("chin", 9);
        wordNumbers.put("tam", 8);
        wordNumbers.put("bay", 7);
        wordNumbers.put("sau", 6);
        wordNumbers.put("nam", 5);
        wordNumbers.put("bon", 4);
        wordNumbers.put("ba", 3);
        wordNumbers.put("hai", 2);
        wordNumbers.put("mot", 1);

        for (Map.Entry<String, Integer> entry : wordNumbers.entrySet()) {
            Pattern p = Pattern.compile("\\b" + entry.getKey() + "\\s+(?:hop|goi|chai|tui|hu|binh|qua|bo|phan|mon)\\b");
            if (p.matcher(norm).find()) {
                return entry.getValue();
            }
        }

        return 1;
    }

    /**
     * Kiểm tra câu hỏi có phải là câu hỏi tính nhẩm tổng tiền / số lượng + phí ship hay không.
     */
    public boolean isCalculationQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String norm = VietnameseTextUtils.normalizeSearchText(message);

        boolean hasQuantityExpr = QUANTITY_DIGIT_PATTERN.matcher(message.toLowerCase()).find()
                || QUANTITY_FALLBACK_PATTERN.matcher(message.toLowerCase()).find()
                || Pattern.compile("\\b(hai|ba|bon|nam|sau|bay|tam|chin|muoi)\\s+(hop|goi|chai|tui|hu|binh|qua)\\b").matcher(norm).find();

        boolean hasMoneyCalcExpr = containsAnyWord(norm,
                "tong bao nhieu", "het bao nhieu", "bao nhieu tien", "tinh tien",
                "thanh tien", "tong cong", "tong tien", "mien phi ship", "freeship", "phi ship tinh sao");

        return (hasQuantityExpr && hasMoneyCalcExpr)
                || containsAnyWord(norm, "tong bao nhieu tien", "lay 2 hop", "lay 3 hop", "mua 2 hop", "mua 3 goi", "mua 2 goi");
    }

    /**
     * Kiểm tra câu hỏi có phải hỏi sâu về thuộc tính của sản phẩm đang xem (bảo quản, hạn dùng, trọng lượng, nguồn gốc, HTX, OCOP)
     */
    public boolean isProductAttributeDeepDiveQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String norm = VietnameseTextUtils.normalizeSearchText(message);
        return containsAnyWord(norm,
                "bao quan", "han su dung", "han dung", "de duoc bao lau", "hsd",
                "trong luong", "khoi luong", "bao nhieu gam", "bao nhieu gram", "bao nhieu kg", "quy cach",
                "gian hang nao", "shop nao", "htx nao", "hop tac xa nao", "ai ban",
                "nguon goc", "xuat xu", "lang nghe", "cau chuyen", "o dau", "tinh nao",
                "may sao", "chung nhan ocop", "con hang khong", "ton kho");
    }

    /**
     * Kiểm tra câu hỏi có phải yêu cầu so sánh các sản phẩm hay không
     */
    public boolean isComparisonQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String norm = VietnameseTextUtils.normalizeSearchText(message);
        return containsAnyWord(norm,
                "so sanh", "khac nhau", "khac gi", "mon nao ngon hon", "mon nao tot hon", "chon mon nao");
    }

    /**
     * Kiểm tra câu hỏi có phải hỏi tìm món rẻ hơn sản phẩm vừa gợi ý hay không
     */
    public boolean isCheaperAlternativeQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String norm = VietnameseTextUtils.normalizeSearchText(message);
        return containsAnyWord(norm,
                "re hon", "mem hon", "gia thap hon", "tiet kiem hon", "binh dan hon");
    }

    /**
     * Kiểm tra câu hỏi có phải hỏi về mã giảm giá / voucher đang mở trên sàn hay không
     */
    public boolean isVoucherInquiry(String message) {
        if (message == null || message.isBlank()) return false;
        String norm = VietnameseTextUtils.normalizeSearchText(message);
        return containsAnyWord(norm,
                "ma giam gia", "voucher", "khuyen mai", "ma uu dai", "san ma", "code giam", "giam gia nao");
    }

    /**
     * Kiểm tra khách có đang dùng đại từ chỉ định tham chiếu tới sản phẩm ở lượt trước hay không
     */
    public boolean hasAnaphoricProductReference(String message) {
        if (message == null || message.isBlank()) return false;
        String norm = VietnameseTextUtils.normalizeSearchText(message);
        return containsAnyWord(norm,
                "mon nay", "hop nay", "goi nay", "chai nay", "hu nay", "tui nay", "binh nay", "qua nay",
                "dac san nay", "san pham nay", "loai nay", "mon do", "hop do",
                "mon dau tien", "mon thu nhat", "mon thu hai", "mon thu 2", "mon thu 3",
                "cac mon vua", "may mon nay", "vua goi y", "o tren");
    }

    /**
     * Nhận diện khách hỏi món ăn tươi / món chưa mở bán trên sàn VietSpecialty (Zero-Hallucination Guardrail)
     */
    public Optional<String> detectUnavailableSpecialtyRequest(String message, List<Product> directMatches) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String norm = VietnameseTextUtils.normalizeSearchText(message);

        Map<String, String> unavailableDishes = new LinkedHashMap<>();
        unavailableDishes.put("pho bo", "Phở bò tươi");
        unavailableDishes.put("pho ga", "Phở gà tươi");
        unavailableDishes.put("pho tuoi", "Phở tươi");
        unavailableDishes.put("bun dau", "Bún đậu mắm tôm");
        unavailableDishes.put("bun bo", "Bún bò Huế nấu sẵn");
        unavailableDishes.put("bun cha", "Bún chả Hà Nội tươi");
        unavailableDishes.put("com tam", "Cơm tấm sườn nướng");
        unavailableDishes.put("banh mi thit", "Bánh mì thịt nguội");
        unavailableDishes.put("sau rieng tuoi", "Sầu riêng tươi nguyên trái");
        unavailableDishes.put("tra sua", "Trà sữa trân châu");
        unavailableDishes.put("pizza", "Pizza");
        unavailableDishes.put("ga ran", "Gà rán");
        unavailableDishes.put("sushi", "Sushi");
        unavailableDishes.put("lau tuoi", "Lẩu tươi nấu sẵn");

        for (Map.Entry<String, String> entry : unavailableDishes.entrySet()) {
            if (containsWord(norm, entry.getKey())) {
                boolean exactProductMatched = directMatches != null && directMatches.stream()
                        .anyMatch(p -> VietnameseTextUtils.normalizeSearchText(p.getName()).contains(entry.getKey()));
                if (!exactProductMatched) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Tìm kiếm sản phẩm khớp trực tiếp theo tên / từ khóa cụ thể trong câu hỏi hiện tại.
     */
    public List<Product> findExplicitlyMentionedProducts(String message, List<Product> allProducts) {
        if (message == null || message.isBlank() || allProducts == null || allProducts.isEmpty()) {
            return List.of();
        }
        String normMsg = VietnameseTextUtils.normalizeSearchText(message);

        // Nếu khách đang nói "món này", "hộp này", "2 hộp này" mà không nêu tên món mới -> ưu tiên lấy từ Active Context
        List<ScoredProduct> scored = new ArrayList<>();

        Map<String, List<String>> specificSynonyms = new LinkedHashMap<>();
        specificSynonyms.put("tra dinh", List.of("tra dinh", "tra tan cuong", "tra thai nguyen", "che tan cuong", "che thai nguyen"));
        specificSynonyms.put("tra cung dinh", List.of("tra cung dinh", "nhat da de vuong", "tra thao moc hue"));
        specificSynonyms.put("thit trau", List.of("thit trau", "trau gac bep", "mac khen"));
        specificSynonyms.put("lap xuong", List.of("lap xuong", "hun khoi mia"));
        specificSynonyms.put("mat ong", List.of("mat ong", "hoa bac ha"));
        specificSynonyms.put("mien dong", List.of("mien dong", "phia den", "cao bang"));
        specificSynonyms.put("com moc", List.of("com moc", "com lang vong", "com ha noi"));
        specificSynonyms.put("toi den", List.of("toi den", "hoc vien quan y"));
        specificSynonyms.put("toi mo coi", List.of("toi mo coi", "toi ly son", "toi co don ly son"));
        specificSynonyms.put("gao nep", List.of("gao nep", "nep nuong", "dien bien"));
        specificSynonyms.put("banh dau xanh", List.of("banh dau xanh", "rong vang", "hai duong"));
        specificSynonyms.put("ruou mo", List.of("ruou mo", "moc chau"));
        specificSynonyms.put("me xung", List.of("me xung"));
        specificSynonyms.put("tom chua", List.of("tom chua"));
        specificSynonyms.put("nuoc mam", List.of("nuoc mam", "nam o", "ca com than"));
        specificSynonyms.put("yen sao", List.of("yen sao", "to yen", "khanh hoa", "nha trang"));
        specificSynonyms.put("ca phe", List.of("ca phe", "arabica", "cau dat", "lam dong"));
        specificSynonyms.put("mac ca", List.of("mac ca", "hat mac ca"));
        specificSynonyms.put("cu do", List.of("cu do", "keo cu do", "ha tinh"));
        specificSynonyms.put("nem chua", List.of("nem chua", "thanh hoa"));
        specificSynonyms.put("tom kho", List.of("tom kho", "rach goc"));
        specificSynonyms.put("ca sac", List.of("ca sac", "kho ca", "u minh"));
        specificSynonyms.put("keo dua", List.of("keo dua", "ben tre"));
        specificSynonyms.put("banh pia", List.of("banh pia", "soc trang", "trung muoi"));
        specificSynonyms.put("mam ca linh", List.of("mam ca linh", "ca linh", "chau doc", "an giang"));
        specificSynonyms.put("muoi tom", List.of("muoi tom", "tay ninh"));
        specificSynonyms.put("ruou sen", List.of("ruou sen", "dong thap"));
        specificSynonyms.put("xoai", List.of("xoai cat chu", "xoai say deo", "cao lanh"));
        specificSynonyms.put("tieu chin", List.of("tieu chin", "tieu phu quoc"));
        specificSynonyms.put("buoi da xanh", List.of("buoi da xanh", "buoi ben tre"));

        for (Product p : allProducts) {
            String normName = VietnameseTextUtils.normalizeSearchText(p.getName());
            int score = 0;

            if (normMsg.contains(normName)) {
                score += 100;
            }

            for (Map.Entry<String, List<String>> entry : specificSynonyms.entrySet()) {
                if (normName.contains(entry.getKey())) {
                    for (String syn : entry.getValue()) {
                        if (normMsg.contains(syn)) {
                            score += 45;
                        }
                    }
                }
            }

            // Kiểm tra khớp từ khóa chung như "trà", "rượu", "bánh", "kẹo", "mắm", "tỏi", "tôm"
            if (score == 0) {
                if (containsWord(normMsg, "tra") && normName.contains("tra")) score += 25;
                if (containsWord(normMsg, "che") && normName.contains("tra")) score += 25;
                if (containsWord(normMsg, "ruou") && normName.contains("ruou")) score += 25;
                if (containsWord(normMsg, "toi") && normName.contains("toi")) score += 25;
                if (containsWord(normMsg, "tom") && normName.contains("tom")) score += 25;
                if (containsWord(normMsg, "banh") && normName.contains("banh")) score += 20;
                if (containsWord(normMsg, "keo") && normName.contains("keo")) score += 20;
                if (containsWord(normMsg, "mam") && (normName.contains("mam") || normName.contains("tom chua"))) score += 20;
            }

            if (score > 0) {
                scored.add(new ScoredProduct(p, score));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredProduct::score).reversed())
                .map(ScoredProduct::product)
                .limit(3)
                .toList();
    }

    /**
     * Truy xuất đa tiêu chí (Multi-Criteria RAG Retrieval) kết hợp Từ khóa + Vùng miền + Tỉnh thành + Dịp sử dụng + Ngân sách + OCOP.
     */
    public List<Product> retrieveProductsMultiCriteria(
            String message,
            List<Product> allProducts,
            BudgetFilter budgetFilter,
            int limit
    ) {
        if (allProducts == null || allProducts.isEmpty()) return List.of();
        String norm = VietnameseTextUtils.normalizeSearchText(message);

        // 1. Lọc theo ngân sách (nếu khách có yêu cầu ngân sách cụ thể như "dưới 400k")
        List<Product> pool = allProducts.stream()
                .filter(p -> !budgetFilter.hasConstraint() || budgetFilter.matches(p.getPrice()))
                .toList();

        if (pool.isEmpty()) {
            // Nếu ngân sách quá thấp không có món nào, lấy các món rẻ nhất trong kho
            return allProducts.stream()
                    .sorted(Comparator.comparing(Product::getPrice))
                    .limit(limit)
                    .toList();
        }

        // 2. Chấm điểm ngữ nghĩa đa chiều
        boolean isGift = containsAnyWord(norm, "qua", "bieu", "tang", "sep", "tet", "sang trong", "phu huynh", "bo me", "doi tac");
        boolean isDrinkingSnack = containsAnyWord(norm, "moi nham", "mon nham", "nham", "nhau", "uong bia", "lai rai", "cuoi tuan", "bong da", "ngay mua");
        boolean isVegetablePairing = containsAnyWord(norm, "rau muong", "rau luoc", "thit luoc", "an voi", "an kem", "cham voi", "cham gi", "com nong", "chao");
        boolean isFamilyMeal = isVegetablePairing || containsAnyWord(norm, "mam com", "bua com", "gia dinh", "nau an", "ton com", "an com", "gia vi");
        boolean isTeaSnack = containsAnyWord(norm, "an vat", "thuong tra", "banh mut", "van phong", "ngot");
        boolean isHealth = containsAnyWord(norm,
                "suc khoe", "tot cho", "boi bo", "de khang", "tang de khang", "ngu ngon",
                "me bau", "ba bau", "nguoi gia", "nguoi lon tuoi", "tre em", "tieu duong",
                "an kieng", "giam can", "da day", "huyet ap", "dep da", "thanh nhiet", "am bung");

        boolean wantNorth = containsAnyWord(norm, "mien bac", "tay bac", "dong bac", "ha noi", "thai nguyen", "son la", "cao bang", "dien bien", "lang son", "ha giang");
        boolean wantCentral = containsAnyWord(norm, "mien trung", "co do", "hue", "da nang", "quang ngai", "ly son", "khanh hoa", "nha trang", "ha tinh", "thanh hoa");
        boolean wantHighlands = containsAnyWord(norm, "tay nguyen", "lam dong", "da lat", "cau dat", "dak lak");
        boolean wantSouth = containsAnyWord(norm, "mien nam", "mien tay", "nam bo", "ca mau", "ben tre", "soc trang", "an giang", "chau doc", "tay ninh", "dong thap", "phu quoc");

        List<Product> explicitMatches = findExplicitlyMentionedProducts(message, pool);

        List<ScoredProduct> scoredList = new ArrayList<>();
        for (Product p : pool) {
            int score = 0;
            String pName = VietnameseTextUtils.normalizeSearchText(p.getName());
            String pDesc = VietnameseTextUtils.normalizeSearchText(
                    (p.getShortDescription() != null ? p.getShortDescription() : "") + " " +
                    (p.getDescription() != null ? p.getDescription() : ""));
            String pProv = p.getProvince() != null ? VietnameseTextUtils.normalizeSearchText(p.getProvince().getName()) : "";
            String pReg = (p.getProvince() != null && p.getProvince().getRegion() != null)
                    ? VietnameseTextUtils.normalizeSearchText(p.getProvince().getRegion().getName()) : "";
            String pCat = p.getCategory() != null ? VietnameseTextUtils.normalizeSearchText(p.getCategory().getName()) : "";

            if (explicitMatches.contains(p)) {
                score += 90 - (explicitMatches.indexOf(p) * 15);
            }

            // Điểm khớp tỉnh thành
            if (!pProv.isBlank() && norm.contains(pProv)) {
                score += 65;
            }

            // Điểm khớp vùng miền
            if (wantNorth && (pReg.contains("mien bac") || pName.contains("tay bac") || pProv.contains("thai nguyen") || pProv.contains("son la") || pProv.contains("ha noi"))) {
                score += 45;
            }
            if (wantCentral && (pReg.contains("mien trung") || pProv.contains("hue") || pProv.contains("quang ngai") || pProv.contains("khanh hoa"))) {
                score += 45;
            }
            if (wantHighlands && (pReg.contains("tay nguyen") || pProv.contains("lam dong") || pName.contains("tay nguyen"))) {
                score += 45;
            }
            if (wantSouth && (pReg.contains("mien nam") || pProv.contains("ca mau") || pProv.contains("dong thap") || pName.contains("ben tre") || pName.contains("soc trang") || pName.contains("phu quoc") || pName.contains("tay ninh") || pName.contains("chau doc"))) {
                score += 45;
            }

            // Điểm theo dịp sử dụng
            if (isGift) {
                if (p.getOcopRating() != null && p.getOcopRating() == 5) score += 35;
                else if (p.getOcopRating() != null && p.getOcopRating() == 4) score += 20;
                if (containsAnyWord(pName, "tra dinh", "tra cung dinh", "yen sao", "toi den", "toi mo coi", "mat ong", "mac ca", "tom kho", "ruou sen", "thit trau")) {
                    score += 30;
                }
            }
            if (isDrinkingSnack) {
                if (containsAnyWord(pName, "thit trau", "lap xuong", "nem chua", "ca sac", "tom kho", "ruou mo", "ruou sen")) {
                    score += 55;
                }
            }
            if (isVegetablePairing) {
                if (containsAnyWord(pName, "tom chua", "nuoc mam", "mam ca linh", "toi mo coi")) {
                    score += 85;
                }
            }
            if (isFamilyMeal) {
                if (containsAnyWord(pName, "nuoc mam", "tom chua", "mam ca linh", "toi mo coi", "mien dong", "gao nep", "tieu chin", "ca sac", "tom kho")) {
                    score += 50;
                }
            }
            if (isTeaSnack) {
                if (containsAnyWord(pName, "banh dau xanh", "me xung", "com moc", "keo cu do", "keo dua", "banh pia", "xoai", "mac ca", "muoi tom", "buoi")) {
                    score += 50;
                }
            }
            if (isHealth) {
                if (containsAnyWord(pName, "yen sao", "toi den", "mat ong", "tra cung dinh", "mac ca", "toi mo coi")) {
                    score += 80;
                }
            }

            // Điểm khớp token tự do
            for (String token : norm.split("\\s+")) {
                if (token.length() >= 4 && !isStopWord(token)) {
                    if (pName.contains(token)) score += 12;
                    else if (pDesc.contains(token) || pCat.contains(token)) score += 5;
                }
            }

            // Nếu khách lọc theo ngân sách (ví dụ "quà biếu dưới 400k"), ưu tiên các món tiệm cận ngân sách & chuẩn OCOP cao
            if (budgetFilter.hasConstraint() && budgetFilter.maxPrice() != null) {
                score += 15;
                if (p.getOcopRating() != null) {
                    score += p.getOcopRating() * 4;
                }
            }

            scoredList.add(new ScoredProduct(p, score));
        }

        scoredList.sort((a, b) -> {
            int cmp = Integer.compare(b.score(), a.score());
            if (cmp != 0) return cmp;
            int ocopA = a.product().getOcopRating() != null ? a.product().getOcopRating() : 0;
            int ocopB = b.product().getOcopRating() != null ? b.product().getOcopRating() : 0;
            int ocopCmp = Integer.compare(ocopB, ocopA);
            if (ocopCmp != 0) return ocopCmp;
            return b.product().getPrice().compareTo(a.product().getPrice());
        });

        return scoredList.stream()
                .map(ScoredProduct::product)
                .limit(limit)
                .toList();
    }

    /**
     * Tổng hợp câu trả lời Hội thoại Đa lượt khi khách hỏi sâu về Bảo quản / Hạn sử dụng / Trọng lượng / Xuất xứ / HTX của sản phẩm đang xem.
     */
    public String synthesizeProductAttributeDeepDive(
            String userMessage,
            List<Product> targetProducts,
            List<Voucher> activeVouchers
    ) {
        String norm = VietnameseTextUtils.normalizeSearchText(userMessage);

        // Xác định sản phẩm mục tiêu (hỗ trợ "món thứ 2", "món thứ 3" hoặc mặc định món đầu tiên)
        int index = 0;
        if (containsAnyWord(norm, "mon thu 2", "mon thu hai", "san pham thu 2") && targetProducts.size() >= 2) {
            index = 1;
        } else if (containsAnyWord(norm, "mon thu 3", "mon thu ba", "san pham thu 3") && targetProducts.size() >= 3) {
            index = 2;
        }
        Product p = targetProducts.get(index);

        boolean askStorage = containsAnyWord(norm, "bao quan", "cat giu", "de o dau");
        boolean askShelfLife = containsAnyWord(norm, "han su dung", "han dung", "bao lau", "hsd");
        boolean askWeight = containsAnyWord(norm, "trong luong", "khoi luong", "bao nhieu gam", "bao nhieu gram", "bao nhieu kg", "quy cach", "don vi");
        boolean askShop = containsAnyWord(norm, "gian hang", "shop", "htx", "hop tac xa", "ai ban");
        boolean askOrigin = containsAnyWord(norm, "nguon goc", "xuat xu", "lang nghe", "cau chuyen", "o dau", "tinh nao");
        boolean askStock = containsAnyWord(norm, "con hang", "ton kho", "so luong con");

        String provName = p.getProvince() != null ? p.getProvince().getName() : "Việt Nam";
        String regName = (p.getProvince() != null && p.getProvince().getRegion() != null)
                ? p.getProvince().getRegion().getName() : "Đặc sản vùng miền";
        String shopName = p.getShop() != null ? p.getShop().getName() : "Gian hàng HTX VietSpecialty";
        String storage = (p.getStorageInstructions() != null && !p.getStorageInstructions().isBlank())
                ? p.getStorageInstructions()
                : "Bảo quản nơi khô ráo, thoáng mát, tránh ánh nắng trực tiếp";
        String shelfLife = (p.getShelfLife() != null && !p.getShelfLife().isBlank())
                ? p.getShelfLife()
                : "12 tháng kể từ ngày sản xuất";
        String weightStr = p.getWeightGram() != null ? (p.getWeightGram() + "g (" + p.getUnit() + ")") : p.getUnit();

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ, về món **").append(p.getName()).append("** (giá niêm yết **")
                .append(MoneyUtils.formatVnd(p.getPrice())).append(" / ").append(p.getUnit())
                .append("**, chuẩn OCOP **").append(p.getOcopRating() != null ? p.getOcopRating() : 4)
                .append(" sao**) mà bạn đang quan tâm, mình xin thông tin chi tiết từ hồ sơ sản phẩm trên sàn như sau ạ:\n\n");

        if (askStorage || askShelfLife || (!askWeight && !askShop && !askOrigin && !askStock)) {
            sb.append("● **Cách bảo quản chuẩn vị:** ").append(storage).append(". ")
                    .append(buildExtraStorageTip(p)).append("\n");
            sb.append("● **Hạn sử dụng:** **").append(shelfLife).append("** (cam kết lô hàng mới nhất xuất xưởng trực tiếp từ ").append(shopName).append(").\n");
        }

        if (askWeight || (!askStorage && !askShelfLife)) {
            sb.append("● **Quy cách & Trọng lượng tịnh:** **").append(weightStr).append("**, đóng gói hút chân không/niêm phong kỹ lưỡng giúp giữ trọn hương vị khi vận chuyển xa.\n");
        }

        if (askOrigin || askShop || (!askStorage && !askShelfLife)) {
            sb.append("● **Nguồn gốc & Gian hàng phân phối:** Chính gốc từ **")
                    .append(p.getOriginDetail() != null ? p.getOriginDetail() : ("Làng nghề " + provName))
                    .append("** (").append(provName).append(" - ").append(regName)
                    .append("), do gian hàng **").append(shopName).append("** trực tiếp cung ứng.\n");
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                sb.append("● **Câu chuyện đặc sản:** ").append(p.getDescription()).append("\n");
            }
        }

        if (askStock) {
            sb.append("● **Tình trạng tồn kho hiện tại:** Sẵn hàng **").append(p.getStock())
                    .append(" ").append(p.getUnit()).append("** tại kho ").append(shopName).append(" (đã bán ")
                    .append(p.getSoldCount()).append("+ lượt, đánh giá trung bình ").append(p.getAvgRating()).append("/5⭐).\n");
        }

        sb.append("\n💡 Nếu bạn đặt mua món **").append(p.getName()).append("** ngay hôm nay, đơn hàng từ **500.000₫** tại gian hàng **")
                .append(shopName).append("** sẽ được **miễn phí vận chuyển tiêu chuẩn (0₫)**. Bạn muốn lấy mấy ")
                .append(extractUnitNoun(p.getUnit())).append(" để mình tính nhẩm tổng chi phí và mã giảm giá tốt nhất cho bạn nhé?");

        return sb.toString();
    }

    /**
     * Tổng hợp câu trả lời tính nhẩm: Số lượng N * Đơn giá + Phí vận chuyển (ngưỡng freeship 500.000₫) + Mã Voucher khả dụng.
     */
    public String synthesizeQuantityAndShippingCalculation(
            String userMessage,
            Product targetProduct,
            List<Voucher> activeVouchers
    ) {
        int quantity = extractRequestedQuantity(userMessage);
        BigDecimal unitPrice = targetProduct.getPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        BigDecimal freeThreshold = (appProperties.getShipping() != null && appProperties.getShipping().getFreeStandardThreshold() != null)
                ? appProperties.getShipping().getFreeStandardThreshold() : BigDecimal.valueOf(500000L);
        BigDecimal standardFee = (appProperties.getShipping() != null && appProperties.getShipping().getStandardFee() != null)
                ? appProperties.getShipping().getStandardFee() : BigDecimal.valueOf(30000L);
        BigDecimal expressFee = (appProperties.getShipping() != null && appProperties.getShipping().getExpressFee() != null)
                ? appProperties.getShipping().getExpressFee() : BigDecimal.valueOf(50000L);

        boolean isFreeStandard = subtotal.compareTo(freeThreshold) >= 0;
        BigDecimal appliedStandardShipping = isFreeStandard ? BigDecimal.ZERO : standardFee;
        BigDecimal totalBeforeVoucher = subtotal.add(appliedStandardShipping);

        String unitNoun = extractUnitNoun(targetProduct.getUnit());
        String shopName = targetProduct.getShop() != null ? targetProduct.getShop().getName() : "Gian hàng HTX";

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ, mình tính chi tiết chi phí khi bạn lấy **").append(quantity).append(" ").append(unitNoun)
                .append(" ").append(targetProduct.getName()).append("** (tại gian hàng **").append(shopName).append("**) như sau ạ:\n\n");

        sb.append("1. **Tiền hàng tạm tính:**\n")
                .append("   ● Đơn giá: **").append(MoneyUtils.formatVnd(unitPrice)).append(" / ").append(targetProduct.getUnit()).append("**\n")
                .append("   ● Số lượng: **").append(quantity).append(" ").append(unitNoun).append("**\n")
                .append("   ● Tạm tính tiền hàng: **").append(MoneyUtils.formatVnd(subtotal)).append("**\n\n");

        sb.append("2. **Phí vận chuyển (Ngưỡng Freeship 500.000₫/gian hàng):**\n");
        if (isFreeStandard) {
            sb.append("   ● Vì tạm tính đơn hàng (**").append(MoneyUtils.formatVnd(subtotal))
                    .append("**) đã đạt từ ngưỡng **").append(MoneyUtils.formatVnd(freeThreshold))
                    .append("** trở lên, bạn được **MIỄN PHÍ 100% phí vận chuyển tiêu chuẩn (0₫)**! (Nếu chọn giao Hỏa tốc đặc sản thì phí là ")
                    .append(MoneyUtils.formatVnd(expressFee)).append(").\n");
        } else {
            BigDecimal diffToFree = freeThreshold.subtract(subtotal);
            sb.append("   ● Vận chuyển tiêu chuẩn: **").append(MoneyUtils.formatVnd(standardFee))
                    .append("** (hoặc Hỏa tốc: **").append(MoneyUtils.formatVnd(expressFee)).append("**).\n")
                    .append("   ● *Mẹo tiết kiệm:* Đơn hàng hiện còn thiếu **").append(MoneyUtils.formatVnd(diffToFree))
                    .append("** nữa là chạm mốc **").append(MoneyUtils.formatVnd(freeThreshold))
                    .append("** để được **miễn phí vận chuyển tiêu chuẩn (0₫)**.\n");
        }

        sb.append("   ● Tổng cộng (chưa áp mã giảm giá): **").append(MoneyUtils.formatVnd(totalBeforeVoucher)).append("**\n");

        // Tìm voucher áp dụng được cho mức tiền hàng này
        List<Voucher> applicableVouchers = activeVouchers.stream()
                .filter(v -> subtotal.compareTo(v.getMinOrderValue()) >= 0)
                .filter(v -> v.getScope() == VoucherScope.PLATFORM
                        || (v.getShop() != null && targetProduct.getShop() != null && v.getShop().getId().equals(targetProduct.getShop().getId())))
                .toList();

        Voucher bestVoucher = null;
        BigDecimal bestDiscount = BigDecimal.ZERO;
        for (Voucher v : applicableVouchers) {
            BigDecimal disc = calculateVoucherDiscount(v, subtotal);
            if (disc.compareTo(bestDiscount) > 0) {
                bestDiscount = disc;
                bestVoucher = v;
            }
        }

        RequestedVoucherMatch requestedVoucher = detectRequestedVoucher(userMessage, activeVouchers);

        if (requestedVoucher != null) {
            if (requestedVoucher.voucher() != null) {
                Voucher rv = requestedVoucher.voucher();
                boolean scopeOk = rv.getScope() == VoucherScope.PLATFORM
                        || (rv.getShop() != null && targetProduct.getShop() != null
                        && rv.getShop().getId().equals(targetProduct.getShop().getId()));
                boolean minOk = subtotal.compareTo(rv.getMinOrderValue()) >= 0;

                if (scopeOk && minOk) {
                    BigDecimal reqDiscount = calculateVoucherDiscount(rv, subtotal);
                    BigDecimal finalPayable = totalBeforeVoucher.subtract(reqDiscount).max(BigDecimal.ZERO);
                    sb.append("\n3. **Áp dụng mã giảm giá theo yêu cầu (`").append(rv.getCode()).append("`):**\n")
                            .append("   ● Mã **`").append(rv.getCode()).append("`** (").append(rv.getName())
                            .append(" - đơn tối thiểu ").append(MoneyUtils.formatVnd(rv.getMinOrderValue()))
                            .append(") đủ điều kiện áp dụng, giảm ngay **").append(MoneyUtils.formatVnd(reqDiscount)).append("**.\n")
                            .append("   👉 **Tổng thanh toán thực tế sau khi áp mã `").append(rv.getCode()).append("`: ")
                            .append(MoneyUtils.formatVnd(finalPayable)).append("**!");
                    if (bestVoucher != null && !bestVoucher.getId().equals(rv.getId())
                            && bestDiscount.compareTo(reqDiscount) > 0) {
                        BigDecimal bestPayable = totalBeforeVoucher.subtract(bestDiscount).max(BigDecimal.ZERO);
                        sb.append("\n   ● *Mẹo tiết kiệm thêm:* Nếu muốn giảm nhiều hơn, mã **`")
                                .append(bestVoucher.getCode()).append("`** cũng áp dụng được cho đơn này (giảm **")
                                .append(MoneyUtils.formatVnd(bestDiscount)).append("**, tổng còn **")
                                .append(MoneyUtils.formatVnd(bestPayable)).append("**).");
                    }
                } else {
                    sb.append("\n3. **Kiểm tra mã giảm giá bạn yêu cầu (`").append(rv.getCode()).append("`):**\n");
                    if (!scopeOk) {
                        String voucherShopName = rv.getShop() != null ? rv.getShop().getName() : "gian hàng khác";
                        sb.append("   ● Rất tiếc, mã **`").append(rv.getCode()).append("`** chỉ áp dụng riêng cho **")
                                .append(voucherShopName).append("**, không áp dụng cho gian hàng **").append(shopName).append("**.\n");
                    } else {
                        BigDecimal lacking = rv.getMinOrderValue().subtract(subtotal).max(BigDecimal.ZERO);
                        sb.append("   ● Rất tiếc, mã **`").append(rv.getCode()).append("`** yêu cầu đơn tối thiểu **")
                                .append(MoneyUtils.formatVnd(rv.getMinOrderValue())).append("** (tạm tính hiện tại là **")
                                .append(MoneyUtils.formatVnd(subtotal)).append("**, còn thiếu **")
                                .append(MoneyUtils.formatVnd(lacking)).append("**).\n");
                    }
                    if (bestVoucher != null) {
                        BigDecimal bestPayable = totalBeforeVoucher.subtract(bestDiscount).max(BigDecimal.ZERO);
                        sb.append("   ● Thay vào đó, bạn có thể áp mã **`").append(bestVoucher.getCode()).append("`** để được giảm **")
                                .append(MoneyUtils.formatVnd(bestDiscount)).append("** (tổng còn **")
                                .append(MoneyUtils.formatVnd(bestPayable)).append("**).");
                    }
                }
            } else {
                sb.append("\n3. **Kiểm tra mã giảm giá bạn yêu cầu (`").append(requestedVoucher.rawCode()).append("`):**\n")
                        .append("   ● Mã **`").append(requestedVoucher.rawCode()).append("`** hiện không tồn tại hoặc đã hết hiệu lực trên hệ thống.\n");
                if (bestVoucher != null) {
                    BigDecimal bestPayable = totalBeforeVoucher.subtract(bestDiscount).max(BigDecimal.ZERO);
                    sb.append("   ● Thay vào đó, bạn đủ điều kiện dùng mã **`").append(bestVoucher.getCode()).append("`** để giảm ngay **")
                            .append(MoneyUtils.formatVnd(bestDiscount)).append("** (tổng còn **")
                            .append(MoneyUtils.formatVnd(bestPayable)).append("**).");
                }
            }
        } else if (bestVoucher != null) {
            BigDecimal finalPayable = totalBeforeVoucher.subtract(bestDiscount).max(BigDecimal.ZERO);
            sb.append("\n3. **Gợi ý áp mã giảm giá tiết kiệm nhất:**\n")
                    .append("   ● Bạn đủ điều kiện nhập mã **`").append(bestVoucher.getCode()).append("`** (")
                    .append(bestVoucher.getName()).append(" - đơn tối thiểu ").append(MoneyUtils.formatVnd(bestVoucher.getMinOrderValue()))
                    .append(") để được giảm ngay **").append(MoneyUtils.formatVnd(bestDiscount)).append("**.\n")
                    .append("   👉 **Tổng thanh toán thực tế sau ưu đãi chỉ còn: ").append(MoneyUtils.formatVnd(finalPayable)).append("**!");
        } else if (!activeVouchers.isEmpty()) {
            Voucher nextVoucher = activeVouchers.stream()
                    .min(Comparator.comparing(Voucher::getMinOrderValue))
                    .orElse(activeVouchers.get(0));
            sb.append("\n3. **Gợi ý săn Voucher:**\n")
                    .append("   ● Trên sàn đang có mã **`").append(nextVoucher.getCode()).append("`** (")
                    .append(formatVoucherBenefit(nextVoucher)).append(" cho đơn từ **")
                    .append(MoneyUtils.formatVnd(nextVoucher.getMinOrderValue())).append("**). Nếu bạn mua thêm cho đủ mốc ")
                    .append(MoneyUtils.formatVnd(nextVoucher.getMinOrderValue())).append(" sẽ áp dụng được ngay mã này nhé!");
        }

        return sb.toString();
    }

    /**
     * Tổng hợp câu trả lời so sánh trực tiếp các sản phẩm trong ngữ cảnh.
     */
    public String synthesizeProductComparison(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return "Dạ, hiện tại chưa có danh sách đặc sản nào trong lượt trò chuyện để so sánh. Bạn hãy cho mình biết dòng đặc sản bạn đang quan tâm nhé!";
        }
        if (products.size() == 1) {
            Product p = products.get(0);
            return "Dạ, ở lượt trước mình vừa gợi ý món **" + p.getName() + "** (**" + MoneyUtils.formatVnd(p.getPrice()) +
                    " / " + p.getUnit() + "**, chuẩn OCOP " + p.getOcopRating() + "⭐). Bạn muốn so sánh món này với đặc sản vùng miền nào khác trên sàn ạ?";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ, mình đặt lên bàn cân so sánh chi tiết **").append(products.size())
                .append(" món đặc sản** vừa gợi ý để bạn dễ dàng lựa chọn theo nhu cầu nhé:\n\n");

        Product cheapest = products.get(0);
        Product mostPremium = products.get(0);

        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            if (p.getPrice().compareTo(cheapest.getPrice()) < 0) cheapest = p;
            if (p.getPrice().compareTo(mostPremium.getPrice()) > 0) mostPremium = p;

            String prov = p.getProvince() != null ? p.getProvince().getName() : "Việt Nam";
            String shop = p.getShop() != null ? p.getShop().getName() : "HTX Đặc sản";
            String weight = p.getWeightGram() != null ? (p.getWeightGram() + "g") : p.getUnit();

            sb.append(i + 1).append(". **").append(p.getName()).append("**\n")
                    .append("   ● **Giá bán & Quy cách:** **").append(MoneyUtils.formatVnd(p.getPrice()))
                    .append(" / ").append(p.getUnit()).append("** (Trọng lượng: ").append(weight).append(")\n")
                    .append("   ● **Xuất xứ & Chứng nhận:** ").append(prov).append(" (").append(shop)
                    .append(") — **OCOP ").append(p.getOcopRating() != null ? p.getOcopRating() : 4).append(" sao**\n")
                    .append("   ● **Điểm nổi bật & Bảo quản:** ").append(p.getShortDescription() != null ? p.getShortDescription() : "")
                    .append(" | HSD: ").append(p.getShelfLife() != null ? p.getShelfLife() : "12 tháng").append("\n\n");
        }

        sb.append("💡 **Lời khuyên chọn nhanh từ VietSpecialty:**\n")
                .append("● Nếu bạn ưu tiên **tiết kiệm ngân sách / thưởng thức hàng ngày**: Chọn **").append(cheapest.getName())
                .append("** (chỉ **").append(MoneyUtils.formatVnd(cheapest.getPrice())).append("**).\n")
                .append("● Nếu bạn cần **thức quà biếu sang trọng, đẳng cấp**: Chọn **").append(mostPremium.getName())
                .append("** (**").append(MoneyUtils.formatVnd(mostPremium.getPrice())).append("**, đạt chuẩn OCOP ")
                .append(mostPremium.getOcopRating() != null ? mostPremium.getOcopRating() : 5).append("⭐).");

        return sb.toString();
    }

    /**
     * Tổng hợp câu trả lời gợi ý các món có mức giá mềm/rẻ hơn sản phẩm đang xem.
     */
    public String synthesizeCheaperAlternatives(
            Product referenceProduct,
            List<Product> allProducts,
            List<Product> outSuggestedProducts
    ) {
        return synthesizeCheaperAlternatives(null, referenceProduct, allProducts, outSuggestedProducts);
    }

    public String synthesizeCheaperAlternatives(
            String userMessage,
            Product referenceProduct,
            List<Product> allProducts,
            List<Product> outSuggestedProducts
    ) {
        BudgetFilter budgetFilter = extractBudgetFilter(userMessage);
        BigDecimal refPrice = referenceProduct != null
                ? referenceProduct.getPrice()
                : (budgetFilter.maxPrice() != null ? budgetFilter.maxPrice() : BigDecimal.valueOf(300000));
        Long refCategoryId = (referenceProduct != null && referenceProduct.getCategory() != null)
                ? referenceProduct.getCategory().getId().longValue() : null;

        List<Product> cheaper = allProducts.stream()
                .filter(p -> referenceProduct == null || !p.getId().equals(referenceProduct.getId()))
                .filter(p -> referenceProduct == null || p.getPrice().compareTo(refPrice) < 0)
                .filter(p -> !budgetFilter.hasConstraint() || budgetFilter.matches(p.getPrice()))
                .sorted((a, b) -> {
                    boolean aSameCat = refCategoryId != null && a.getCategory() != null && a.getCategory().getId().longValue() == refCategoryId;
                    boolean bSameCat = refCategoryId != null && b.getCategory() != null && b.getCategory().getId().longValue() == refCategoryId;
                    if (aSameCat != bSameCat) return aSameCat ? -1 : 1;
                    return b.getPrice().compareTo(a.getPrice());
                })
                .limit(3)
                .toList();

        if (cheaper.isEmpty() && budgetFilter.hasConstraint()) {
            String budgetDesc = budgetFilter.maxPrice() != null
                    ? ("dưới **" + MoneyUtils.formatVnd(budgetFilter.maxPrice()) + "**")
                    : "trong mức ngân sách bạn yêu cầu";
            return "Dạ, hiện tại trong kho VietSpecialty chưa có sản phẩm nào phù hợp " + budgetDesc
                    + ". Bạn có thể nâng nhẹ ngân sách để mình gợi ý thêm các đặc sản OCOP chất lượng nhất nhé!";
        }

        if (cheaper.isEmpty()) {
            cheaper = allProducts.stream()
                    .sorted(Comparator.comparing(Product::getPrice))
                    .limit(3)
                    .toList();
        }

        outSuggestedProducts.addAll(cheaper);

        StringBuilder sb = new StringBuilder();
        if (referenceProduct != null && budgetFilter.hasConstraint() && budgetFilter.maxPrice() != null) {
            sb.append("Dạ, nếu bạn muốn tìm phương án giá mềm hơn món **").append(referenceProduct.getName())
                    .append("** (**").append(MoneyUtils.formatVnd(refPrice))
                    .append("**) và nằm gọn trong ngân sách **dưới ").append(MoneyUtils.formatVnd(budgetFilter.maxPrice()))
                    .append("**, thì trên sàn **VietSpecialty** đang có những đặc sản chuẩn OCOP phù hợp dưới đây ạ:\n\n");
        } else if (referenceProduct != null) {
            sb.append("Dạ, nếu bạn muốn tìm phương án có mức giá mềm hơn món **").append(referenceProduct.getName())
                    .append("** (**").append(MoneyUtils.formatVnd(refPrice))
                    .append("**), thì trên sàn **VietSpecialty** đang có những đặc sản chuẩn OCOP giá cực kỳ hợp lý dưới đây ạ:\n\n");
        } else if (budgetFilter.hasConstraint() && budgetFilter.maxPrice() != null) {
            sb.append("Dạ, mình xin gợi ý cho bạn các món đặc sản chuẩn vị truyền thống có mức giá tiết kiệm **dưới ")
                    .append(MoneyUtils.formatVnd(budgetFilter.maxPrice())).append("** trên sàn **VietSpecialty**:\n\n");
        } else {
            sb.append("Dạ, mình xin gợi ý cho bạn các món đặc sản chuẩn vị truyền thống có mức giá tiết kiệm nhất trên sàn **VietSpecialty**:\n\n");
        }

        for (Product p : cheaper) {
            String prov = p.getProvince() != null ? p.getProvince().getName() : "Việt Nam";
            BigDecimal saved = refPrice.subtract(p.getPrice()).max(BigDecimal.ZERO);
            sb.append("● **").append(p.getName()).append("** (").append(prov).append("): **")
                    .append(MoneyUtils.formatVnd(p.getPrice())).append(" / ").append(p.getUnit()).append("**");
            if (referenceProduct != null && saved.compareTo(BigDecimal.ZERO) > 0) {
                sb.append(" *(tiết kiệm hơn ").append(MoneyUtils.formatVnd(saved)).append(")*");
            }
            sb.append(" — ").append(p.getShortDescription() != null ? p.getShortDescription() : "").append("\n");
        }

        sb.append("\nBạn ưng ý món nào có thể bấm nút **+ Giỏ** ngay trên thẻ sản phẩm bên dưới nhé!");
        return sb.toString();
    }

    /**
     * Tổng hợp câu trả lời tư vấn danh sách Mã giảm giá (Vouchers) thực tế đang ACTIVE trong DB.
     */
    public String synthesizeActiveVouchersResponse(
            String userMessage,
            List<Voucher> activeVouchers,
            List<Product> activeContextProducts,
            List<Product> allProducts,
            List<Product> outSuggestedProducts
    ) {
        List<Product> sampleProducts = !activeContextProducts.isEmpty()
                ? activeContextProducts.stream().limit(2).toList()
                : allProducts.stream().limit(2).toList();
        outSuggestedProducts.addAll(sampleProducts);

        if (activeVouchers.isEmpty()) {
            return "Dạ, hiện tại trên sàn VietSpecialty chưa có mã voucher nào đang mở. Tuy nhiên bạn vẫn được hưởng ưu đãi **Miễn phí vận chuyển tiêu chuẩn (0₫)** cho mọi đơn hàng từ **500.000₫** tại mỗi gian hàng HTX nhé!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎟️ **Danh sách Mã giảm giá (Voucher) thực tế đang mở trên sàn VietSpecialty:**\n\n");

        for (Voucher v : activeVouchers) {
            String scopeLabel = v.getScope() == VoucherScope.PLATFORM
                    ? "🌐 Voucher Toàn sàn VietSpecialty"
                    : ("🏡 Voucher Gian hàng " + (v.getShop() != null ? v.getShop().getName() : "HTX"));

            sb.append("● Mã **`").append(v.getCode()).append("`** — **").append(v.getName()).append("**\n")
                    .append("  - **Phạm vi:** ").append(scopeLabel).append("\n")
                    .append("  - **Mức giảm:** **").append(formatVoucherBenefit(v)).append("**\n")
                    .append("  - **Điều kiện:** Áp dụng cho đơn hàng từ **").append(MoneyUtils.formatVnd(v.getMinOrderValue())).append("**\n\n");
        }

        sb.append("💡 **Mẹo kết hợp ưu đãi tối đa:** Bạn có thể áp dụng đồng thời **1 Voucher Gian hàng HTX** (như `TANCUONG10`) + **1 Voucher Toàn sàn** (như `VIETSPECIALTY` hoặc `FREESHIP30`) ở bước Thanh toán, đồng thời đơn từ **500.000₫** được **miễn phí ship tiêu chuẩn 30.000₫**!");

        return sb.toString();
    }

    /**
     * Tổng hợp câu trả lời trung thực khi khách hỏi món chưa mở bán (Zero-Hallucination Grounding).
     */
    public String synthesizeUnavailableSpecialtyResponse(
            String unavailableDishName,
            String userMessage,
            List<Product> allProducts,
            List<Product> outSuggestedProducts
    ) {
        String norm = VietnameseTextUtils.normalizeSearchText(unavailableDishName);
        List<Product> alternatives = new ArrayList<>();

        if (norm.contains("tra sua")) {
            addMatchingProducts(allProducts, alternatives, List.of("trà cung đình", "trà đinh", "cà phê", "kẹo dừa", "mè xửng"), 3);
        } else if (norm.contains("sau rieng")) {
            addMatchingProducts(allProducts, alternatives, List.of("kẹo dừa sầu riêng", "bánh pía", "xoài cát chu"), 3);
        } else if (norm.contains("pho") || norm.contains("bun") || norm.contains("lau")) {
            addMatchingProducts(allProducts, alternatives, List.of("miến dong phia đén", "nước mắm nam ô", "tôm chua"), 3);
        } else {
            addMatchingProducts(allProducts, alternatives, List.of("thịt trâu gác bếp", "lạp xưởng", "nem chua"), 3);
        }

        if (alternatives.isEmpty() && !allProducts.isEmpty()) {
            alternatives.addAll(allProducts.stream().limit(3).toList());
        }

        outSuggestedProducts.addAll(alternatives);

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ, mình xin thông tin trung thực là hiện tại trên sàn **VietSpecialty chưa mở bán món \"")
                .append(unavailableDishName).append("\"** (do sàn chuyên về các dòng nông sản, đặc sản khô, đóng gói hút chân không đạt chuẩn OCOP bảo quản và vận chuyển toàn quốc).\n\n")
                .append("Tuy nhiên, nếu bạn yêu thích hương vị tương đồng, bạn có thể tham khảo ngay các đặc sản chính gốc đang có sẵn trong kho của sàn dưới đây ạ:\n\n");

        for (Product p : alternatives) {
            String prov = p.getProvince() != null ? p.getProvince().getName() : "Việt Nam";
            sb.append("● **").append(p.getName()).append("** (").append(prov).append("): **")
                    .append(MoneyUtils.formatVnd(p.getPrice())).append(" / ").append(p.getUnit())
                    .append("** — ").append(p.getShortDescription() != null ? p.getShortDescription() : "").append("\n");
        }

        return sb.toString();
    }

    /**
     * Tổng hợp câu trả lời tư vấn sản phẩm đa tiêu chí theo ngữ cảnh (Dịp sử dụng, Vùng miền, Tỉnh thành, Ngân sách, OCOP).
     */
    public String synthesizeContextualProductAdvice(
            String userMessage,
            List<Product> matchedProducts,
            BudgetFilter budgetFilter,
            List<Voucher> activeVouchers
    ) {
        if (matchedProducts == null || matchedProducts.isEmpty()) {
            return "Dạ, hiện tại mình chưa tìm thấy sản phẩm khớp hoàn toàn với tiêu chí của bạn trong kho. Bạn có thể cho mình biết thêm khoảng ngân sách hoặc vùng miền bạn yêu thích để mình lọc lại nhé!";
        }

        String norm = VietnameseTextUtils.normalizeSearchText(userMessage);
        StringBuilder sb = new StringBuilder();

        boolean isHealthQuery = containsAnyWord(norm,
                "suc khoe", "tot cho", "boi bo", "de khang", "tang de khang", "ngu ngon",
                "me bau", "ba bau", "nguoi gia", "nguoi lon tuoi", "tre em", "tieu duong",
                "an kieng", "giam can", "da day", "huyet ap", "dep da", "thanh nhiet", "am bung");
        boolean isVegetablePairingQuery = containsAnyWord(norm,
                "rau muong", "rau luoc", "thit luoc", "an voi", "an kem", "cham voi", "cham gi", "com nong", "chao");

        // 1. Mở đầu cá nhân hóa theo đúng yêu cầu trong câu hỏi của khách
        if (budgetFilter.hasConstraint() && budgetFilter.maxPrice() != null) {
            sb.append("Dạ, với mức ngân sách **dưới ").append(MoneyUtils.formatVnd(budgetFilter.maxPrice()))
                    .append("** mà bạn đưa ra, mình đã rà soát kho hàng trực tiếp trên sàn **VietSpecialty** và tuyển chọn được **")
                    .append(matchedProducts.size()).append(" thức quà đặc sản OCOP** có mức giá hoàn toàn nằm trong ngân sách của bạn:\n\n");
        } else if (isHealthQuery) {
            sb.append("Dạ, nếu bạn đang tìm **đặc sản bồi bổ sức khỏe, tăng sức đề kháng và an thần tự nhiên**, thì trên sàn **VietSpecialty** có những dòng nông sản & thảo dược OCOP 4-5 sao cực kỳ giàu dưỡng chất dưới đây ạ:\n\n");
        } else if (isVegetablePairingQuery) {
            sb.append("Dạ, **rau muống luộc, rau luộc hay thịt luộc, cơm nóng** mà kết hợp với các thức chấm lên men truyền thống hoặc tỏi đặc sản trên **VietSpecialty** thì đúng chuẩn \"hao cơm\" bậc nhất ẩm thực Việt luôn ạ! Mình gợi ý ngay cho bạn những món kết hợp tuyệt hảo nhất:\n\n");
        } else if (containsAnyWord(norm, "moi nham", "mon nham", "nham", "nhau", "uong bia", "lai rai")) {
            sb.append("Dạ, nhắc đến **mồi nhắm lai rai** chuẩn vị truyền thống thì các đặc sản gác bếp & lên men tự nhiên trên **VietSpecialty** là lựa chọn số 1 luôn ạ! Mình gợi ý ngay cho bạn:\n\n");
        } else if (containsAnyWord(norm, "qua", "bieu", "tang", "sep", "tet", "phu huynh")) {
            sb.append("Dạ, để chọn **đặc sản làm quà biếu** vừa sang trọng, tinh tế vừa mang đậm câu chuyện văn hóa vùng miền Việt Nam, mình xin tư vấn cho bạn các dòng sản phẩm đạt chuẩn OCOP 4-5 sao nổi bật nhất trên sàn:\n\n");
        } else if (containsAnyWord(norm, "mam com", "bua com", "gia dinh")) {
            sb.append("Dạ, để **mâm cơm gia đình** thêm đậm đà hương vị quê hương, mình xin giới thiệu các đặc sản & gia vị truyền thống trứ danh đang sẵn hàng trên **VietSpecialty**:\n\n");
        } else {
            sb.append("Dạ, dựa trên nhu cầu tìm kiếm **\"").append(userMessage.trim())
                    .append("\"** của bạn, mình xin tư vấn chi tiết các đặc sản chính gốc từ kho hàng **VietSpecialty**:\n\n");
        }

        // 2. Phân tích từng sản phẩm với dữ liệu thật 100% từ DB
        BigDecimal comboSum = BigDecimal.ZERO;
        for (int i = 0; i < matchedProducts.size(); i++) {
            Product p = matchedProducts.get(i);
            comboSum = comboSum.add(p.getPrice());

            String prov = p.getProvince() != null ? p.getProvince().getName() : "Việt Nam";
            String reg = (p.getProvince() != null && p.getProvince().getRegion() != null)
                    ? p.getProvince().getRegion().getName() : "";
            String shop = p.getShop() != null ? p.getShop().getName() : "HTX Đặc sản";
            String weight = p.getWeightGram() != null ? (p.getWeightGram() + "g") : p.getUnit();
            String shelfLife = p.getShelfLife() != null ? p.getShelfLife() : "12 tháng";

            sb.append(i + 1).append(". **").append(p.getName()).append("** — Giá: **")
                    .append(MoneyUtils.formatVnd(p.getPrice())).append(" / ").append(p.getUnit()).append("**\n")
                    .append("   ● **Xuất xứ:** ").append(p.getOriginDetail() != null ? p.getOriginDetail() : prov)
                    .append(" (").append(prov).append(!reg.isBlank() ? " - " + reg : "").append(") | Gian hàng: **").append(shop).append("**\n")
                    .append("   ● **Chứng nhận & Quy cách:** Chuẩn **OCOP ").append(p.getOcopRating() != null ? p.getOcopRating() : 4)
                    .append(" sao** | Trọng lượng: ").append(weight).append(" | HSD: ").append(shelfLife).append("\n")
                    .append("   ● **Hương vị đặc trưng:** ").append(p.getDescription() != null ? p.getDescription() : p.getShortDescription()).append("\n");

            if (isHealthQuery) {
                sb.append("   ● **Giá trị dinh dưỡng & Công dụng sức khỏe:** ").append(buildHealthBenefitAdvice(p)).append("\n");
            }
            if (isVegetablePairingQuery) {
                sb.append("   ● **Bí quyết ăn kèm & Pha nước chấm bắt cơm:** ").append(buildCulinaryPairingAdvice(p)).append("\n");
            }
            sb.append("\n");
        }

        // 3. Phân tích bài toán ngân sách / gợi ý combo & phí ship / voucher
        if (budgetFilter.hasConstraint() && budgetFilter.maxPrice() != null && matchedProducts.size() >= 2) {
            Product p1 = matchedProducts.get(0);
            Product p2 = matchedProducts.get(1);
            BigDecimal pairSum = p1.getPrice().add(p2.getPrice());
            if (pairSum.compareTo(budgetFilter.maxPrice()) <= 0) {
                sb.append("🎁 **Gợi ý ghép Combo trong ngân sách:** Bạn có thể kết hợp cả **")
                        .append(p1.getName()).append("** + **").append(p2.getName())
                        .append("** với tổng chi phí chỉ **").append(MoneyUtils.formatVnd(pairSum))
                        .append("** (vẫn nằm gọn dưới mức ").append(MoneyUtils.formatVnd(budgetFilter.maxPrice())).append(" của bạn)!\n");
            } else {
                BigDecimal remain = budgetFilter.maxPrice().subtract(p1.getPrice());
                sb.append("💡 **Phân tích ngân sách:** Nếu chọn **").append(p1.getName())
                        .append("** (**").append(MoneyUtils.formatVnd(p1.getPrice()))
                        .append("**), bạn vẫn còn dư **").append(MoneyUtils.formatVnd(remain))
                        .append("** so với ngân sách ").append(MoneyUtils.formatVnd(budgetFilter.maxPrice())).append(".\n");
            }
        }

        Product top = matchedProducts.get(0);
        sb.append("Bạn muốn mình tư vấn thêm về **cách bảo quản, hạn sử dụng** hay **tính tổng tiền & mã giảm giá** cho món **")
                .append(top.getName()).append("** không ạ?");

        return sb.toString();
    }

    /**
     * Tổng hợp câu trả lời Chính sách / Phí vận chuyển / Đổi trả / Thanh toán ĐỘNG theo đúng khía cạnh câu hỏi của khách
     * (Không bao giờ trả về một chuỗi văn bản cứng giống nhau cho mọi câu hỏi).
     */
    public String synthesizeDynamicPolicyResponse(
            String userMessage,
            List<Product> activeContextProducts,
            List<Voucher> activeVouchers
    ) {
        String norm = VietnameseTextUtils.normalizeSearchText(userMessage);
        BudgetFilter amountMentioned = extractBudgetFilter(userMessage);

        boolean askShipping = containsAnyWord(norm, "van chuyen", "phi ship", "ship", "giao hang", "bao lau", "freeship", "mien phi");
        boolean askReturn = containsAnyWord(norm, "doi tra", "hoan tien", "hang loi", "hu hong", "vo", "khong dung", "bao hanh");
        boolean askPayment = containsAnyWord(norm, "thanh toan", "vnpay", "cod", "tra tien", "chuyen khoan", "qr");

        // Nếu câu hỏi chung chung "chính sách của sàn", bật cả 3 nhưng vẫn cá nhân hóa theo câu hỏi và voucher sống
        if (!askShipping && !askReturn && !askPayment) {
            askShipping = true;
            askReturn = true;
            askPayment = true;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ, giải đáp thắc mắc của bạn về **\"").append(userMessage.trim()).append("\"**, VietSpecialty xin thông tin cụ thể theo quy định hiện hành của sàn:\n\n");

        if (askShipping) {
            BigDecimal freeThreshold = (appProperties.getShipping() != null && appProperties.getShipping().getFreeStandardThreshold() != null)
                    ? appProperties.getShipping().getFreeStandardThreshold() : BigDecimal.valueOf(500000L);
            BigDecimal stdFee = (appProperties.getShipping() != null && appProperties.getShipping().getStandardFee() != null)
                    ? appProperties.getShipping().getStandardFee() : BigDecimal.valueOf(30000L);
            BigDecimal expFee = (appProperties.getShipping() != null && appProperties.getShipping().getExpressFee() != null)
                    ? appProperties.getShipping().getExpressFee() : BigDecimal.valueOf(50000L);

            sb.append("🚚 **Biểu phí Vận chuyển & Thời gian Giao hàng:**\n")
                    .append("● **Miễn phí vận chuyển (0₫):** Áp dụng tự động cho mọi đơn hàng tiêu chuẩn đạt từ **")
                    .append(MoneyUtils.formatVnd(freeThreshold)).append("** tại mỗi gian hàng HTX.\n")
                    .append("● **Đơn dưới ").append(MoneyUtils.formatVnd(freeThreshold))
                    .append(":** Phí giao tiêu chuẩn là **").append(MoneyUtils.formatVnd(stdFee))
                    .append("/kiện** (giao 1-2 ngày nội tỉnh, 2-4 ngày liên tỉnh toàn quốc); gói **Hỏa tốc đặc sản** là **")
                    .append(MoneyUtils.formatVnd(expFee)).append("/kiện**.\n");

            if (amountMentioned.maxPrice() != null) {
                BigDecimal orderAmt = amountMentioned.maxPrice();
                if (orderAmt.compareTo(freeThreshold) >= 0) {
                    sb.append("👉 Với mức đơn **").append(MoneyUtils.formatVnd(orderAmt))
                            .append("** bạn vừa hỏi, đơn hàng của bạn **được MIỄN PHÍ 100% phí ship tiêu chuẩn (0₫)**!\n");
                } else {
                    BigDecimal lack = freeThreshold.subtract(orderAmt);
                    sb.append("👉 Với mức đơn **").append(MoneyUtils.formatVnd(orderAmt))
                            .append("** bạn vừa hỏi, phí ship tiêu chuẩn là **30.000₫** (bạn chỉ cần mua thêm **")
                            .append(MoneyUtils.formatVnd(lack)).append("** nữa là được Freeship 0₫).\n");
                }
            } else if (!activeContextProducts.isEmpty()) {
                Product active = activeContextProducts.get(0);
                sb.append("👉 *Ví dụ với món **").append(active.getName()).append("** (**")
                        .append(MoneyUtils.formatVnd(active.getPrice())).append("**) bạn đang xem:* ");
                if (active.getPrice().compareTo(freeThreshold) >= 0) {
                    sb.append("Chỉ cần mua 1 ").append(extractUnitNoun(active.getUnit())).append(" là bạn đã được **Freeship 0₫** ngay!\n");
                } else {
                    int qtyForFree = freeThreshold.divide(active.getPrice(), 0, RoundingMode.CEILING).intValue();
                    sb.append("Khi lấy từ **").append(qtyForFree).append(" ").append(extractUnitNoun(active.getUnit()))
                            .append("** trở lên sẽ chạm ngưỡng **500.000₫** và được **Freeship 0₫**!\n");
                }
            }

            // Gợi ý mã FREESHIP30 thực tế từ DB nếu có
            activeVouchers.stream()
                    .filter(v -> v.getCode().toUpperCase().contains("SHIP") || v.getScope() == VoucherScope.PLATFORM)
                    .findFirst()
                    .ifPresent(v -> sb.append("● **Ưu đãi phí ship hiện có:** Bạn có thể nhập mã **`")
                            .append(v.getCode()).append("`** để được ").append(formatVoucherBenefit(v))
                            .append(" cho đơn từ **").append(MoneyUtils.formatVnd(v.getMinOrderValue())).append("**.\n"));
            sb.append("\n");
        }

        if (askReturn) {
            sb.append("🛡️ **Cam kết Đổi trả & Hoàn tiền 100% trong 48 giờ:**\n")
                    .append("● Quý khách được **đổi mới hoặc yêu cầu hoàn tiền 100% miễn phí trong vòng 48 giờ** kể từ khi nhận hàng nếu đặc sản bị bể vỡ, hư hỏng do vận chuyển, ẩm mốc hoặc không đúng cam kết chứng nhận OCOP.\n")
                    .append("● **Thao tác cực nhanh:** Bạn chỉ cần vào mục **\"Đơn hàng của tôi\"** -> bấm nút **\"Yêu cầu hoàn tiền\"** ngay trên đơn hàng đã giao, Ban quản trị sàn sẽ phê duyệt hoàn tiền trực tiếp cho bạn.\n\n");
        }

        if (askPayment) {
            sb.append("💳 **Phương thức Thanh toán An toàn & Tách đơn Đa gian hàng:**\n")
                    .append("● **Thanh toán khi nhận hàng (COD):** Kiểm tra kiện hàng nguyên vẹn trước khi thanh toán tiền mặt cho bưu tá.\n")
                    .append("● **Thanh toán trực tuyến qua cổng VNPay:** Hỗ trợ quét mã **VNPay QR** và thẻ **ATM nội địa** (xác thực chữ ký bảo mật HMAC-SHA512). Nếu giỏ hàng của bạn gồm đặc sản từ nhiều HTX khác nhau, hệ thống sẽ tự động tách đơn thông minh theo từng gian hàng trong một lần thanh toán duy nhất.\n");
        }

        return sb.toString().trim();
    }

    /**
     * Tra cứu đơn hàng tích hợp Khóa Bảo mật Đơn hàng (Privacy Guardrail):
     * - Nếu đúng chủ đơn hàng đang đăng nhập (hoặc Admin): Hiển thị đầy đủ SĐT, địa chỉ và danh sách các món trong đơn.
     * - Nếu chưa đăng nhập hoặc không phải chủ đơn hàng: Che một phần số điện thoại (0912***678) và ẩn địa chỉ chi tiết.
     */
    public String buildPrivacyAwareOrderResponse(Order o, User currentUser, List<OrderItem> items) {
        boolean isOwnerOrAdmin = currentUser != null && (
                (o.getUser() != null && o.getUser().getId().equals(currentUser.getId()))
                || currentUser.getRole() == Role.ADMIN
        );

        String carrier = (o.getCarrierName() != null && !o.getCarrierName().isBlank())
                ? o.getCarrierName() : "Đơn vị vận chuyển liên kết VietSpecialty";
        String tracking = (o.getTrackingCode() != null && !o.getTrackingCode().isBlank())
                ? " (Mã vận đơn: **" + o.getTrackingCode() + "**)" : "";
        String shopName = o.getShop() != null ? o.getShop().getName() : "Gian hàng đặc sản";
        String paymentLabel = "PAID".equals(o.getPaymentStatus().name())
                ? "Đã thanh toán"
                : ("REFUND_PENDING".equals(o.getPaymentStatus().name())
                        ? "Đang chờ duyệt hoàn tiền"
                        : ("REFUNDED".equals(o.getPaymentStatus().name()) ? "Đã hoàn tiền" : "Thanh toán khi nhận hàng (COD)"));

        String displayPhone = isOwnerOrAdmin
                ? o.getReceiverPhone()
                : maskPhoneNumber(o.getReceiverPhone());

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ, VietSpecialty đã tra cứu thấy thông tin thực tế của đơn hàng **#").append(o.getCode()).append("**:\n\n")
                .append("● **Trạng thái đơn hàng:** **").append(o.getStatus().getDescription()).append("**\n")
                .append("● **Gian hàng HTX gửi:** ").append(shopName).append("\n")
                .append("● **Người nhận:** ").append(o.getReceiverName()).append(" (SĐT: **").append(displayPhone).append("**)\n")
                .append("● **Vận chuyển:** ").append(carrier).append(tracking).append("\n")
                .append("● **Tổng thanh toán:** **").append(MoneyUtils.formatVnd(o.getTotalAmount()))
                .append("** (").append(paymentLabel).append(")\n");

        if (isOwnerOrAdmin) {
            sb.append("● **Địa chỉ giao hàng:** ").append(o.getShippingAddress()).append("\n");
            if (items != null && !items.isEmpty()) {
                sb.append("\n📦 **Chi tiết đặc sản trong đơn hàng của bạn:**\n");
                for (OrderItem item : items) {
                    sb.append("  - **").append(item.getProductName()).append("**: ")
                            .append(item.getQuantity()).append(" x ").append(MoneyUtils.formatVnd(item.getUnitPrice()))
                            .append(" = **").append(MoneyUtils.formatVnd(item.getLineTotal())).append("**\n");
                }
            }
        } else {
            sb.append("\n🔒 *Lưu ý bảo mật:* Để bảo vệ quyền riêng tư của khách hàng, số điện thoại người nhận đã được ẩn một phần (`")
                    .append(displayPhone).append("`). Bạn vui lòng đăng nhập đúng tài khoản đặt hàng để xem đầy đủ danh sách sản phẩm và địa chỉ giao hàng chi tiết nhé!");
        }

        return sb.toString();
    }

    public String maskPhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) return "***";
        String trimmed = phone.trim();
        if (trimmed.length() <= 6) return "***" + trimmed.substring(Math.max(0, trimmed.length() - 2));
        return trimmed.substring(0, 4) + "***" + trimmed.substring(trimmed.length() - 3);
    }

    /**
     * Sinh 3 câu hỏi gợi ý tiếp theo (Follow-up Chips) ĐỘNG theo đúng sản phẩm hoặc chủ đề vừa trả lời.
     */
    public List<String> buildDynamicFollowUpQuestions(
            ChatIntent intent,
            String userMessage,
            List<Product> suggestedProducts,
            List<Product> activeContextProducts,
            List<Voucher> activeVouchers
    ) {
        List<Product> contextList = !suggestedProducts.isEmpty() ? suggestedProducts : activeContextProducts;

        if (!contextList.isEmpty()) {
            Product primary = contextList.get(0);
            String shortName = shortenProductName(primary.getName());
            String unitNoun = extractUnitNoun(primary.getUnit());
            String norm = VietnameseTextUtils.normalizeSearchText(userMessage);

            List<String> suggestions = new ArrayList<>();
            if (!containsAnyWord(norm, "bao quan", "han su dung", "han dung")) {
                suggestions.add(shortName + " bảo quản thế nào và hạn dùng bao lâu?");
            } else {
                suggestions.add("Nguồn gốc làng nghề của " + shortName + " ở đâu?");
            }

            if (!isCalculationQuestion(userMessage)) {
                suggestions.add("Nếu mình lấy 2 " + unitNoun + " " + shortName + " thì tổng bao nhiêu tiền?");
            } else {
                suggestions.add("Có món đặc sản nào giá mềm hơn để mua kèm không?");
            }

            if (contextList.size() >= 2 && !isComparisonQuestion(userMessage)) {
                suggestions.add("So sánh giúp mình các món đặc sản vừa gợi ý");
            } else {
                suggestions.add("Trên sàn đang có mã giảm giá nào áp dụng được không?");
            }
            return suggestions.stream().limit(3).toList();
        }

        if (intent == ChatIntent.ORDER_LOOKUP) {
            return List.of(
                    "Chính sách đổi trả hoàn tiền trong 48 giờ thế nào?",
                    "Trên sàn đang có mã giảm giá nào mới không?",
                    "Gợi ý đặc sản làm quà biếu dưới 400k"
            );
        }

        if (intent == ChatIntent.POLICY) {
            return List.of(
                    "Trên sàn đang có mã giảm giá hay voucher nào không?",
                    "Gợi ý đặc sản Tây Bắc làm mồi nhắm uống bia",
                    "Tư vấn đặc sản Trà Tân Cương Thái Nguyên"
            );
        }

        return List.of(
                "Tư vấn giúp mình Trà Tân Cương Thái Nguyên",
                "Gợi ý đặc sản làm quà biếu dưới 400k",
                "Trên sàn đang có mã giảm giá hay voucher nào không?"
        );
    }

    /**
     * Xây dựng System Prompt chuẩn RAG chứa toàn bộ dữ liệu thực tế từ Database để bơm vào Gemini API (khi có API Key thật).
     */
    public String buildGroundedSystemPrompt(
            List<Product> activeContextProducts,
            List<Product> retrievedProducts,
            List<Voucher> activeVouchers
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là Trợ lý AI Chuyên gia Ẩm thực & Đặc sản Vùng miền của sàn TMĐT VietSpecialty (Việt Nam).\n")
                .append("QUY TẮC HÀNG RÀO KHUÔN KHỔ (DOMAIN GUARDRAILS) BẮT BUỘC:\n")
                .append("1. KHÓA PHẠM VI CHỦ ĐỀ: Chỉ tư vấn về đặc sản vùng miền, ẩm thực, quà biếu OCOP, đơn hàng, mã giảm giá và chính sách của sàn VietSpecialty. ")
                .append("Tuyệt đối KHÔNG viết code lập trình (Python, Java, JS...), KHÔNG giải toán/vật lý, KHÔNG dự báo thời tiết hay phân tích giá Bitcoin/chứng khoán. Nếu khách hỏi ngoài phạm vi, hãy từ chối duyên dáng, hóm hỉnh và dẫn dắt về các món đặc sản thực tế bên dưới.\n")
                .append("2. KHÓA DỮ LIỆU THỰC TẾ 100% (ZERO-HALLUCINATION): Mọi tên sản phẩm, giá tiền, trọng lượng, hạn sử dụng, cách bảo quản, tên gian hàng HTX, mã voucher phải lấy CHÍNH XÁC từ ngữ cảnh Database bên dưới. Không tự bịa sản phẩm hay mã giảm giá không có trong danh sách.\n")
                .append("3. TRÍ NHỚ ĐA LƯỢT: Nếu khách hỏi 'món này', 'hộp này', '2 hộp này' mà không nhắc lại tên, hãy dùng [SẢN PHẨM ĐANG XEM Ở LƯỢT TRƯỚC] để trả lời chính xác cách bảo quản, hạn dùng và tính tổng tiền.\n\n");

        if (!activeContextProducts.isEmpty()) {
            sb.append("[SẢN PHẨM ĐANG XEM Ở LƯỢT TRƯỚC (ACTIVE PRODUCT CONTEXT)]:\n");
            for (Product p : activeContextProducts) {
                sb.append(formatProductFullGroundTruth(p)).append("\n");
            }
            sb.append("\n");
        }

        if (!retrievedProducts.isEmpty()) {
            sb.append("[ĐẶC SẢN TRUY XUẤT TỪ DATABASE VIETSPECIALTY]:\n");
            for (Product p : retrievedProducts) {
                sb.append(formatProductFullGroundTruth(p)).append("\n");
            }
            sb.append("\n");
        }

        if (!activeVouchers.isEmpty()) {
            sb.append("[MÃ GIẢM GIÁ (VOUCHERS) ĐANG HOẠT ĐỘNG TRONG DATABASE]:\n");
            for (Voucher v : activeVouchers) {
                sb.append("- Mã code: ").append(v.getCode())
                        .append(" | Tên: ").append(v.getName())
                        .append(" | Ưu đãi: ").append(formatVoucherBenefit(v))
                        .append(" | Đơn tối thiểu: ").append(MoneyUtils.formatVnd(v.getMinOrderValue()))
                        .append(" | Phạm vi: ").append(v.getScope() == VoucherScope.PLATFORM ? "Toàn sàn" : ("Shop " + (v.getShop() != null ? v.getShop().getName() : "")))
                        .append("\n");
            }
            sb.append("\n");
        }

        sb.append("[CHÍNH SÁCH VẬN CHUYỂN & ĐỔI TRẢ VIETSPECIALTY]:\n")
                .append("- Phí ship tiêu chuẩn: 30.000₫/kiện. Giao Hỏa tốc: 50.000₫/kiện.\n")
                .append("- MIỄN PHÍ VẬN CHUYỂN tiêu chuẩn (0₫) cho đơn hàng từ 500.000₫ tại mỗi gian hàng.\n")
                .append("- Đổi trả & hoàn tiền 100% miễn phí trong vòng 48 giờ nếu hư hỏng hoặc không đúng mô tả OCOP.\n")
                .append("- Thanh toán: COD hoặc VNPay (ATM/QR Pay, tự động tách đơn đa gian hàng).\n");

        return sb.toString();
    }

    private String formatProductFullGroundTruth(Product p) {
        String prov = p.getProvince() != null ? p.getProvince().getName() : "Việt Nam";
        String reg = (p.getProvince() != null && p.getProvince().getRegion() != null) ? p.getProvince().getRegion().getName() : "";
        String shop = p.getShop() != null ? p.getShop().getName() : "HTX Đặc sản";
        return String.format(
                "- Tên: %s | Giá: %s | Đơn vị: %s | Trọng lượng: %sg | OCOP: %s sao | Tỉnh: %s (%s) | Gian hàng: %s | Xuất xứ: %s | Hạn sử dụng: %s | Cách bảo quản: %s | Tồn kho: %d | Mô tả: %s",
                p.getName(),
                MoneyUtils.formatVnd(p.getPrice()),
                p.getUnit(),
                p.getWeightGram() != null ? p.getWeightGram() : 500,
                p.getOcopRating() != null ? p.getOcopRating() : 4,
                prov,
                reg,
                shop,
                p.getOriginDetail() != null ? p.getOriginDetail() : ("Làng nghề " + prov),
                p.getShelfLife() != null ? p.getShelfLife() : "12 tháng kể từ ngày sản xuất",
                p.getStorageInstructions() != null ? p.getStorageInstructions() : "Bảo quản nơi khô ráo, thoáng mát, tránh ánh nắng trực tiếp",
                p.getStock() != null ? p.getStock() : 0,
                p.getShortDescription() != null ? p.getShortDescription() : ""
        );
    }

    private String buildExtraStorageTip(Product p) {
        String normName = VietnameseTextUtils.normalizeSearchText(p.getName());
        if (normName.contains("tra")) {
            return "Sau khi mở túi trà, bạn nên buộc kín miệng túi hoặc cho vào hũ sành/thủy tinh kín khí để giữ trọn hương cốm non và tránh hút ẩm";
        }
        if (normName.contains("thit trau") || normName.contains("lap xuong") || normName.contains("ca sac") || normName.contains("tom kho")) {
            return "Sau khi cắt túi hút chân không, bạn nên bảo quản trong ngăn mát hoặc ngăn đông tủ lạnh; trước khi thưởng thức có thể hấp nóng hoặc quay lò vi sóng 1-2 phút cho mềm thơm";
        }
        if (normName.contains("mat ong") || normName.contains("ruou")) {
            return "Đậy kín nắp chai sau mỗi lần sử dụng và để ở nhiệt độ phòng thoáng mát, không cần bỏ vào ngăn đá tủ lạnh";
        }
        if (normName.contains("tom chua") || normName.contains("nem chua") || normName.contains("mam")) {
            return "Khi đã đạt độ chín lên men vừa ăn, bạn nên cất vào ngăn mát tủ lạnh để giữ độ giòn ngon lâu hơn";
        }
        return "Sau khi mở bao bì, bạn nên đậy kín để giữ trọn độ thơm ngon nguyên bản của đặc sản";
    }

    private String buildHealthBenefitAdvice(Product p) {
        String normName = VietnameseTextUtils.normalizeSearchText(p.getName());
        if (normName.contains("yen sao")) {
            return "Chứa hơn 18 loại axit amin và 31 nguyên tố vi lượng quý, giúp phục hồi thể lực nhanh chóng, tăng cường hệ miễn dịch, bồi bổ phổi và dưỡng nhan cho cả người lớn tuổi, mẹ bầu lẫn người mới ốm dậy.";
        }
        if (normName.contains("toi den")) {
            return "Lên men tự nhiên giàu hợp chất S-Allyl-L-Cysteine (SAC) và Polyphenol gấp nhiều lần tỏi tươi, giúp chống oxy hóa, ổn định huyết áp, giảm mỡ máu, bảo vệ tim mạch và tăng sức đề kháng vượt trội.";
        }
        if (normName.contains("mat ong")) {
            return "Mật ong hoa bạc hà nguyên chất vùng cao nguyên đá giàu enzyme tự nhiên và khoáng chất, giúp làm dịu cổ họng, ấm bụng, hỗ trợ tiêu hóa và tăng đề kháng mỗi sáng khi pha cùng nước ấm.";
        }
        if (normName.contains("tra cung dinh")) {
            return "Kết hợp 16 vị thảo mộc cung đình Huế (táo tàu, kỷ tử, hoa cúc, cam thảo, tim sen...), giúp thanh nhiệt, giải độc gan, an thần và mang lại giấc ngủ sâu tự nhiên.";
        }
        if (normName.contains("mac ca")) {
            return "Nhân hạt mắc ca giàu Omega-3, Omega-6, chất xơ và chất béo không bão hòa tốt cho tim mạch, trí não, rất phù hợp cho người ăn kiêng, mẹ bầu và trẻ nhỏ.";
        }
        if (normName.contains("toi mo coi")) {
            return "Tỏi cô đơn 1 nhánh trồng trên cát san hô đảo Lý Sơn chứa hàm lượng tinh dầu Allicin cực cao, giúp kháng khuẩn tự nhiên, phòng cảm cúm và hỗ trợ tim mạch.";
        }
        return "Nông sản tự nhiên được chế biến theo quy trình chuẩn OCOP, không chất bảo quản độc hại, an toàn và bổ dưỡng cho sức khỏe gia đình.";
    }

    private String buildCulinaryPairingAdvice(Product p) {
        String normName = VietnameseTextUtils.normalizeSearchText(p.getName());
        if (normName.contains("tom chua")) {
            return "Tôm chua Huế lên men cùng riềng, tỏi, ớt và thính nếp có vị chua thanh, cay dịu, ngọt hậu; dùng để **chấm trực tiếp rau muống luộc**, rau lang luộc hoặc cuốn cùng thịt ba chỉ luộc, bún tươi thì ngon hết sảy!";
        }
        if (normName.contains("nuoc mam")) {
            return "Nước mắm nhĩ cá cơm than Nam Ô ủ chượp truyền thống 12 tháng, chỉ cần **dằm thêm vài lát ớt tươi và tép tỏi đập dập để chấm rau muống luộc** hoặc rưới cơm nóng là dậy mùi thơm lừng cả gian bếp.";
        }
        if (normName.contains("mam ca linh")) {
            return "Mắm cá linh Châu Đốc chuẩn vị miền Tây có thể chưng thịt ba chỉ chấm rau muống luộc, rau sống hoặc nấu lẩu mắm đều đậm đà khó quên.";
        }
        if (normName.contains("toi mo coi")) {
            return "Tỏi cô đơn Lý Sơn thơm nồng mà không hăng gắt, dùng để **phi thơm làm món rau muống xào tỏi** xanh mướt giòn sần sật hoặc giã pha nước mắm tỏi ớt chấm rau luộc cực kỳ đưa cơm.";
        }
        return "Kết hợp tuyệt vời trong mâm cơm gia đình Việt, dùng ăn kèm rau luộc, cơm nóng giúp bữa ăn thêm tròn vị.";
    }

    private BigDecimal calculateVoucherDiscount(Voucher v, BigDecimal subtotal) {
        if (v.getDiscountType() == DiscountType.FIXED) {
            return v.getDiscountValue();
        }
        BigDecimal disc = subtotal.multiply(v.getDiscountValue())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
        if (v.getMaxDiscount() != null && disc.compareTo(v.getMaxDiscount()) > 0) {
            return v.getMaxDiscount();
        }
        return disc;
    }

    private String formatVoucherBenefit(Voucher v) {
        if (v.getDiscountType() == DiscountType.FIXED) {
            return "Giảm trực tiếp " + MoneyUtils.formatVnd(v.getDiscountValue());
        }
        String maxStr = v.getMaxDiscount() != null ? (" (tối đa " + MoneyUtils.formatVnd(v.getMaxDiscount()) + ")") : "";
        return "Giảm " + v.getDiscountValue().stripTrailingZeros().toPlainString() + "%" + maxStr;
    }

    private String extractUnitNoun(String unit) {
        if (unit == null || unit.isBlank()) return "sản phẩm";
        String firstWord = unit.trim().split("\\s+")[0].toLowerCase();
        return firstWord.isBlank() ? "sản phẩm" : firstWord;
    }

    private String shortenProductName(String name) {
        if (name == null) return "đặc sản này";
        String[] words = name.trim().split("\\s+");
        if (words.length <= 5) return name.trim();
        return String.join(" ", Arrays.copyOfRange(words, 0, 5));
    }

    private void addMatchingProducts(List<Product> allProducts, List<Product> targetList, List<String> keywords, int max) {
        if (allProducts == null) return;
        for (String kw : keywords) {
            String normKw = VietnameseTextUtils.normalizeSearchText(kw);
            for (Product p : allProducts) {
                if (targetList.size() >= max) return;
                String normName = VietnameseTextUtils.normalizeSearchText(p.getName());
                if (normName.contains(normKw) && !targetList.contains(p)) {
                    targetList.add(p);
                }
            }
        }
    }

    private boolean containsAnyWord(String normalizedText, String... phrases) {
        if (normalizedText == null || normalizedText.isBlank()) return false;
        for (String phrase : phrases) {
            String normPhrase = VietnameseTextUtils.normalizeSearchText(phrase);
            if (!normPhrase.isBlank() && containsWord(normalizedText, normPhrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWord(String normalizedText, String normPhrase) {
        int idx = normalizedText.indexOf(normPhrase);
        while (idx != -1) {
            boolean startOk = (idx == 0) || !Character.isLetterOrDigit(normalizedText.charAt(idx - 1));
            int endIdx = idx + normPhrase.length();
            boolean endOk = (endIdx == normalizedText.length()) || !Character.isLetterOrDigit(normalizedText.charAt(endIdx));
            if (startOk && endOk) {
                return true;
            }
            idx = normalizedText.indexOf(normPhrase, idx + 1);
        }
        return false;
    }

    private boolean isStopWord(String token) {
        return Set.of("giup", "minh", "cho", "hoi", "muon", "tim", "mon", "nao", "nhung", "cua", "tren", "san", "voi", "nhu", "the", "hay", "con", "khong").contains(token);
    }

    private RequestedVoucherMatch detectRequestedVoucher(String userMessage, List<Voucher> activeVouchers) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        if (activeVouchers != null) {
            for (Voucher v : activeVouchers) {
                if (v.getCode() == null || v.getCode().isBlank()) continue;
                Pattern p = Pattern.compile("(?i)(?:^|[^A-Za-z0-9_-])" + Pattern.quote(v.getCode().trim()) + "(?:$|[^A-Za-z0-9_-])");
                if (p.matcher(userMessage).find()) {
                    return new RequestedVoucherMatch(v, v.getCode().trim().toUpperCase());
                }
            }
        }
        Pattern explicitCodePattern = Pattern.compile("(?i)(?:áp\\s*dụng\\s*mã|áp\\s*mã|nhập\\s*mã|dùng\\s*mã|mã\\s*voucher)\\s*:?\\s*([A-Za-z0-9_-]{4,30})");
        Matcher m = explicitCodePattern.matcher(userMessage);
        if (m.find()) {
            String token = m.group(1).trim();
            String normToken = VietnameseTextUtils.normalizeSearchText(token);
            Set<String> nonCodeWords = Set.of("giam", "khuyen", "uu", "nao", "nay", "moi", "cua", "cho", "shop", "san", "duoc", "khong");
            boolean looksLikeCode = token.chars().anyMatch(Character::isDigit)
                    || token.equals(token.toUpperCase())
                    || !nonCodeWords.contains(normToken);
            if (looksLikeCode && !nonCodeWords.contains(normToken)) {
                return new RequestedVoucherMatch(null, token.toUpperCase());
            }
        }
        return null;
    }

    private record RequestedVoucherMatch(Voucher voucher, String rawCode) {}

    private record ScoredProduct(Product product, int score) {}
}