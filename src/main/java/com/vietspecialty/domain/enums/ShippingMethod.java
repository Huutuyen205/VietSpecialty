package com.vietspecialty.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShippingMethod {
    STANDARD("Giao hàng tiêu chuẩn (3-5 ngày)"),
    EXPRESS("Giao hàng hỏa tốc (1-2 ngày)");

    private final String description;
}

