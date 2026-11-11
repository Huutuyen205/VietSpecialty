package com.vietspecialty.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyUtils {

    private static final DecimalFormat VND_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        VND_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private MoneyUtils() {
        // Utility class
    }

    /**
     * Định dạng tiền tệ VND: 125000 -> "125.000₫"
     */
    public static synchronized String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "0₫";
        }
        return VND_FORMAT.format(amount) + "₫";
    }

    /**
     * Chuyển đổi an toàn Long thành BigDecimal
     */
    public static BigDecimal of(long amount) {
        return BigDecimal.valueOf(amount);
    }
}

