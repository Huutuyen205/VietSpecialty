package com.vietspecialty.mapper;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.dto.response.UserResponse;

public final class UserMapper {

    private UserMapper() {
        // Utility class
    }

    public static UserResponse toResponse(User user) {
        if (user == null) return null;

        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getPasswordSet() == null || Boolean.TRUE.equals(user.getPasswordSet())
        );
    }
}

