package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.dto.request.ChangePasswordRequest;
import com.vietspecialty.dto.request.ProfileUpdateRequest;
import com.vietspecialty.dto.response.UserResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.mapper.UserMapper;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.security.SessionInvalidator;
import com.vietspecialty.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionInvalidator sessionInvalidator;

    @Override
    public UserResponse getProfile(Long userId) {
        User user = getUserOrThrow(userId);
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = getUserOrThrow(userId);

        user.setFullName(request.fullName().trim());
        String normalizedPhone = (request.phone() != null && !request.phone().isBlank())
                ? request.phone().trim()
                : null;
        user.setPhone(normalizedPhone);

        User updatedUser = userRepository.save(user);
        log.info("Updated profile for user id={}", userId);
        return UserMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUserOrThrow(userId);
        boolean hasPassword = user.getPasswordSet() == null || Boolean.TRUE.equals(user.getPasswordSet());

        // Kiểm tra mật khẩu cũ (chỉ bắt buộc khi tài khoản đã có mật khẩu)
        if (hasPassword) {
            if (request.oldPassword() == null || request.oldPassword().isBlank()
                    || !passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.WRONG_PASSWORD);
            }
        }

        // Kiểm tra mật khẩu mới trùng khớp
        if (!request.isPasswordMatching()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu xác nhận không khớp");
        }

        // Kiểm tra mật khẩu mới không được trùng mật khẩu cũ (khi đã có mật khẩu)
        if (hasPassword && passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordSet(true);
        userRepository.save(user);
        log.info("Changed password for user id={} (initialSetup={})", userId, !hasPassword);
    }

    @Override
    @Transactional
    public void lockUser(Long targetUserId, Long adminUserId) {
        if (targetUserId.equals(adminUserId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự khóa tài khoản của chính mình");
        }

        User targetUser = getUserOrThrow(targetUserId);
        if (targetUser.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Không thể khóa tài khoản của Quản trị viên khác");
        }

        targetUser.setStatus(UserStatus.LOCKED);
        userRepository.save(targetUser);

        // Hủy toàn bộ phiên làm việc của user bị khóa
        sessionInvalidator.invalidateUserSessions(targetUserId);
        log.warn("Locked user id={} by admin id={}", targetUserId, adminUserId);
    }

    @Override
    @Transactional
    public void unlockUser(Long targetUserId) {
        User targetUser = getUserOrThrow(targetUserId);
        targetUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(targetUser);
        log.info("Unlocked user id={}", targetUserId);
    }

    @Override
    @Transactional
    public void promoteToSeller(Long userId) {
        User user = getUserOrThrow(userId);
        if (user.getRole() == Role.USER) {
            user.setRole(Role.SELLER);
            userRepository.save(user);

            // Làm mới session để cập nhật Authority ROLE_SELLER
            sessionInvalidator.invalidateUserSessions(userId);
            log.info("Promoted user id={} to SELLER role", userId);
        }
    }

    @Override
    public Page<UserResponse> getUsers(String query, Pageable pageable) {
        Page<User> users;
        if (query != null && !query.isBlank()) {
            users = userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(UserMapper::toResponse);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}

