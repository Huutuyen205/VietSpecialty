package com.vietspecialty.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-+");

    private SlugUtils() {
        // Utility class
    }

    /**
     * Tạo slug chuẩn URL từ chuỗi tiếng Việt.
     * Ví dụ: "Chè Thái Nguyên Hảo Hạng!" -> "che-thai-nguyen-hao-hang"
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String noAccents = VietnameseTextUtils.removeAccents(input);
        String noWhitespace = WHITESPACE.matcher(noAccents).replaceAll("-");
        String normalized = NON_LATIN.matcher(noWhitespace).replaceAll("");
        String singleHyphens = MULTI_HYPHEN.matcher(normalized).replaceAll("-");

        return singleHyphens.toLowerCase(Locale.ENGLISH)
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}

