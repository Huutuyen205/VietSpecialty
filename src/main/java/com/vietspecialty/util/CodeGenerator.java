package com.vietspecialty.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CodeGenerator {

    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyMMdd");

    private CodeGenerator() {
        // Utility class
    }

    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Sinh mã nhóm đơn hàng: VSG + yyMMdd + 6 ký tự ngẫu nhiên
     * Ví dụ: VSG261110A1B2C3
     */
    public static String generateOrderGroupCode() {
        return "VSG" + LocalDateTime.now().format(DATE_PATTERN) + randomString(6);
    }

    /**
     * Sinh mã đơn hàng chi tiết từng gian hàng: VS + yyMMdd + 6 ký tự ngẫu nhiên
     * Ví dụ: VS261110X9Y8Z7
     */
    public static String generateOrderCode() {
        return "VS" + LocalDateTime.now().format(DATE_PATTERN) + randomString(6);
    }

    /**
     * Hậu tố ngắn ngẫu nhiên cho slug
     */
    public static String generateShortSuffix() {
        return randomString(4).toLowerCase();
    }
}

