package com.vietspecialty.mapper;

import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.dto.response.ShopResponse;

public final class ShopMapper {

    private ShopMapper() {
        // Utility class
    }

    public static ShopResponse toResponse(Shop s) {
        if (s == null) return null;

        String provinceName = s.getProvince() != null ? s.getProvince().getName() : null;

        return new ShopResponse(
                s.getId(),
                s.getName(),
                s.getSlug(),
                s.getDescription(),
                s.getLogoUrl(),
                s.getPhone(),
                provinceName,
                s.getAddressDetail(),
                s.getStatus(),
                s.getCreatedAt()
        );
    }
}

