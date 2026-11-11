package com.vietspecialty.mapper;

import com.vietspecialty.domain.entity.Notification;
import com.vietspecialty.dto.response.NotificationResponse;

import java.time.Duration;
import java.time.LocalDateTime;

public final class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationResponse toResponse(Notification n) {
        if (n == null) return null;

        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getContent(),
                n.getLinkUrl(),
                n.getIsRead(),
                n.getCreatedAt(),
                formatTimeAgo(n.getCreatedAt())
        );
    }

    private static String formatTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long seconds = duration.getSeconds();

        if (seconds < 60) return "Vừa xong";
        long minutes = duration.toMinutes();
        if (minutes < 60) return minutes + " phút trước";
        long hours = duration.toHours();
        if (hours < 24) return hours + " giờ trước";
        long days = duration.toDays();
        if (days < 30) return days + " ngày trước";
        return dateTime.toLocalDate().toString();
    }
}

