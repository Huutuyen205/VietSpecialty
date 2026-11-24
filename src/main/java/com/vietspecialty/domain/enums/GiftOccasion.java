package com.vietspecialty.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GiftOccasion {
    TET("Tết & Lễ hội"),
    FAMILY("Biếu gia đình"),
    PARTNER("Đối tác & Khách quý"),
    CELEBRATION("Chúc mừng & Kỷ niệm");

    private final String description;
}
