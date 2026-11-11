package com.vietspecialty.dto.response;

import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.UserStatus;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        Role role,
        UserStatus status,
        LocalDateTime createdAt
) {
}

