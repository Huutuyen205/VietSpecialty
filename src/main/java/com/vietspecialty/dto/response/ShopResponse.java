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
        LocalDateTime createdAt,
        String ownerFullName,
        String ownerEmailAddr,
        String rejectReason
) {
    public ShopResponse(
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
        this(id, name, slug, description, logoUrl, phone, provinceName, addressDetail, status, createdAt, null, null, null);
    }

    public String pickupAddress() {
        return addressDetail != null ? addressDetail : provinceName;
    }

    public Double avgRating() {
        return 5.0;
    }

    public String hotline() {
        return phone != null ? phone : "";
    }

    public String ownerName() {
        return ownerFullName != null ? ownerFullName : name;
    }

    public String ownerEmail() {
        return ownerEmailAddr != null ? ownerEmailAddr : "";
    }
}

