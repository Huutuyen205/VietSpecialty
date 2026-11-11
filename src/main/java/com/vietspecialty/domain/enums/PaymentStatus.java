package com.vietspecialty.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    PENDING("Đang xử lý"),
    SUCCESS("Thành công"),
    FAILED("Thất bại"),
    EXPIRED("Hết hạn");

    private final String description;
}

