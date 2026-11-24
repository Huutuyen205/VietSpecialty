package com.vietspecialty.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum GiftWrapType {
    STANDARD_BOX("Hộp quà truyền thống", BigDecimal.ZERO),
    BAMBOO_PREMIUM("Hộp tre đan cao cấp", new BigDecimal("25000"));

    private final String description;
    private final BigDecimal fee;
}
