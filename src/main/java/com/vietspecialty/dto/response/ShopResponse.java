package com.vietspecialty.dto.response;

import com.vietspecialty.domain.enums.ShopStatus;

import java.time.LocalDateTime;

public record ShopResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String phone,
        String provinceName,
        String addressDetail,
        ShopStatus status,
        LocalDateTime createdAt
) {
}

