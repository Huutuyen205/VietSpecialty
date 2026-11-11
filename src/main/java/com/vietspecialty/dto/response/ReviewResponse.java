package com.vietspecialty.dto.response;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long productId,
        String productName,
        Long userId,
        String userFullName,
        String userAvatarUrl,
        Integer rating,
        String content,
        String sellerReply,
        LocalDateTime sellerRepliedAt,
        LocalDateTime createdAt
) {
}

