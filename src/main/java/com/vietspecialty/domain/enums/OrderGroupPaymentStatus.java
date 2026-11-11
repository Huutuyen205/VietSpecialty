package com.vietspecialty.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderGroupPaymentStatus {
    UNPAID("Chưa thanh toán"),
    PAID("Đã thanh toán"),
    FAILED("Thanh toán thất bại"),
    EXPIRED("Hết hạn thanh toán"),
    NOT_REQUIRED("Không yêu cầu thanh toán trước");

    private final String description;
}

