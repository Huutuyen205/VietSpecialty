package com.vietspecialty.service;

import com.vietspecialty.dto.request.ChangePasswordRequest;
import com.vietspecialty.dto.request.ProfileUpdateRequest;
import com.vietspecialty.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse getProfile(Long userId);

    UserResponse updateProfile(Long userId, ProfileUpdateRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    void lockUser(Long targetUserId, Long adminUserId);

    void unlockUser(Long targetUserId);

    void promoteToSeller(Long userId);

    Page<UserResponse> getUsers(String query, Pageable pageable);
}

