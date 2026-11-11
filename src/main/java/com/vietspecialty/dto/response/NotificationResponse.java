package com.vietspecialty.dto.response;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String content,
        String linkUrl,
        Boolean isRead,
        LocalDateTime createdAt,
        String timeAgo
) {}

