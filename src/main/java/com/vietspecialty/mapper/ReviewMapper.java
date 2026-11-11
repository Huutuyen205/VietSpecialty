package com.vietspecialty.mapper;

import com.vietspecialty.domain.entity.Review;
import com.vietspecialty.dto.response.ReviewResponse;

public final class ReviewMapper {

    private ReviewMapper() {
        // Utility class
    }

    public static ReviewResponse toResponse(Review r) {
        if (r == null) return null;

        Long productId = r.getProduct() != null ? r.getProduct().getId() : null;
        String productName = r.getProduct() != null ? r.getProduct().getName() : null;
        Long userId = r.getUser() != null ? r.getUser().getId() : null;
        String userFullName = r.getUser() != null ? r.getUser().getFullName() : null;
        String userAvatarUrl = r.getUser() != null ? r.getUser().getAvatarUrl() : null;

        return new ReviewResponse(
                r.getId(),
                productId,
                productName,
                userId,
                userFullName,
                userAvatarUrl,
                r.getRating(),
                r.getContent(),
                r.getSellerReply(),
                r.getSellerRepliedAt(),
                r.getCreatedAt()
        );
    }
}

