package com.vietspecialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CheckoutFormRequest(
        @NotNull(message = "Vui lòng chọn địa chỉ nhận hàng")
        Long addressId,

        @NotBlank(message = "Vui lòng chọn phương thức thanh toán")
        String paymentMethod,

        @NotBlank(message = "Vui lòng chọn phương thức vận chuyển")
        String shippingMethod,

        String systemVoucherCode,

        Map<Long, String> shopVoucherCodes,

        @NotBlank(message = "Phiên đặt hàng không hợp lệ")
        String checkoutToken,

        String note
) {}

