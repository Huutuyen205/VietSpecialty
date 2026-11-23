package com.vietspecialty.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank
    private String baseUrl = "http://localhost:8080";

    private boolean seedDemo = true;

    private Admin admin = new Admin();
    private Shipping shipping = new Shipping();
    private Order order = new Order();
    private Storage storage = new Storage();
    private Vnpay vnpay = new Vnpay();
    private Google google = new Google();
    private Ai ai = new Ai();

    @Getter
    @Setter
    public static class Admin {
        private String email = "admin@vietspecialty.vn";
        private String password = "Admin@123456";
    }

    @Getter
    @Setter
    public static class Shipping {
        @NotNull
        private BigDecimal standardFee = new BigDecimal("30000");
        @NotNull
        private BigDecimal expressFee = new BigDecimal("50000");
        @NotNull
        private BigDecimal freeStandardThreshold = new BigDecimal("500000");
    }

    @Getter
    @Setter
    public static class Order {
        private int paymentTimeoutMinutes = 15;
        private int autoDeliverDays = 7;
    }

    @Getter
    @Setter
    public static class Storage {
        private String provider = "local";
        private String localDir = "./uploads";
        private int maxImagesPerProduct = 8;
        private String cloudinaryUrl = "";
    }

    @Getter
    @Setter
    public static class Vnpay {
        private String tmnCode = "VIETSPEC";
        private String hashSecret = "VIETSPECIALTYSANDBOXSECRETKEY2026";
        private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
        private String returnUrl = "http://localhost:8080/payment/vnpay/return";
        private boolean processOnReturn = true;

        public String getTmnCode() {
            return (tmnCode == null || tmnCode.isBlank()) ? "VIETSPEC" : tmnCode;
        }

        public String getHashSecret() {
            return (hashSecret == null || hashSecret.isBlank()) ? "VIETSPECIALTYSANDBOXSECRETKEY2026" : hashSecret;
        }
    }

    @Getter
    @Setter
    public static class Google {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:8080/auth/google/callback";
    }

    @Getter
    @Setter
    public static class Ai {
        private String provider = "gemini";
        private Gemini gemini = new Gemini();
        private Chat chat = new Chat();

        @Getter
        @Setter
        public static class Gemini {
            private String apiKey = "";
            private String model = "gemini-1.5-flash";
            private int timeoutSeconds = 20;
        }

        @Getter
        @Setter
        public static class Chat {
            private int maxLength = 500;
            private int historySize = 10;
            private int rateLimit = 15;
            private int rateWindowMinutes = 5;
        }
    }
}

