package com.vietspecialty.service;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {

    void createNotification(User user, String type, String title, String content, String linkUrl);

    Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable);

    List<NotificationResponse> getRecentNotifications(Long userId);

    long getUnreadCount(Long userId);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);
}

