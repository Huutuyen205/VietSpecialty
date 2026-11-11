package com.vietspecialty.dto.request;

import com.vietspecialty.domain.enums.DiscountType;
import com.vietspecialty.domain.enums.VoucherScope;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherCreateRequest(
        @NotBlank(message = "Mã voucher không được để trống")
        @Size(min = 3, max = 50, message = "Mã voucher từ 3 đến 50 ký tự")
        String code,

        @NotBlank(message = "Tên voucher không được để trống")
        @Size(max = 150, message = "Tên voucher tối đa 150 ký tự")
        String name,

        @NotNull(message = "Loại giảm giá không được để trống")
        DiscountType discountType,

        @NotNull(message = "Giá trị giảm không được để trống")
        @DecimalMin(value = "1", message = "Giá trị giảm phải lớn hơn 0")
        BigDecimal discountValue,

        BigDecimal maxDiscount,

        @NotNull(message = "Giá trị đơn tối thiểu không được để trống")
        @DecimalMin(value = "0", message = "Giá trị đơn tối thiểu không âm")
        BigDecimal minOrderValue,

        Integer usageLimit,

        Integer perUserLimit,

        @NotNull(message = "Ngày bắt đầu không được để trống")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startAt,

        @NotNull(message = "Ngày kết thúc không được để trống")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime endAt,

        VoucherScope scope
) {}

