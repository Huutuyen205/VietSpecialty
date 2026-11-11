package com.vietspecialty.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class VietnameseTextUtils {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private VietnameseTextUtils() {
        // Utility class
    }

    /**
     * Bỏ toàn bộ dấu tiếng Việt và chuyển sang chữ thường.
     * Ví dụ: "Nước mắm Phú Quốc" -> "nuoc mam phu quoc"
     */
    public static String removeAccents(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");

        // Xử lý riêng ký tự đ/Đ trong tiếng Việt
        return withoutDiacritics
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .trim()
                .toLowerCase();
    }

    /**
     * Chuẩn hóa văn bản tìm kiếm: bỏ dấu, chữ thường, gộp khoảng trắng thừa.
     * Thích hợp lưu cột search_text và xử lý từ khóa tìm kiếm.
     */
    public static String normalizeSearchText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String unaccented = removeAccents(input);
        return WHITESPACE_PATTERN.matcher(unaccented).replaceAll(" ").trim();
    }

    public static String toSearchableText(String... parts) {
        if (parts == null || parts.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                sb.append(part).append(" ");
            }
        }
        return normalizeSearchText(sb.toString());
    }
}

