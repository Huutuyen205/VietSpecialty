package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.dto.request.RegisterRequest;
import com.vietspecialty.dto.response.UserResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.mapper.UserMapper;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase().trim();

        // 1. Kiểm tra email đã tồn tại
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // 2. Kiểm tra mật khẩu khớp
        if (!request.isPasswordMatching()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu nhập lại không trùng khớp");
        }

        // 3. Tạo tài khoản người dùng mới (Role mặc định USER, Status ACTIVE)
        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .phone(request.phone().trim())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user successfully: id={}, email={}", savedUser.getId(), savedUser.getEmail());

        return UserMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public GoogleAuthResult authenticateWithGoogleWithResult(String email, String fullName, String googleId, String avatarUrl) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Địa chỉ email từ Google không được để trống");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!normalizedEmail.contains("@") || normalizedEmail.length() > 150) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Địa chỉ email Google không hợp lệ");
        }

        Optional<User> existingOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existingOpt.isPresent()) {
            User existingUser = existingOpt.get();
            if (existingUser.getStatus() != UserStatus.ACTIVE) {
                log.warn("Blocked Google login for locked account: email={}", normalizedEmail);
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }

            boolean updated = false;
            if ((existingUser.getAvatarUrl() == null || existingUser.getAvatarUrl().isBlank())
                    && avatarUrl != null && !avatarUrl.isBlank()) {
                existingUser.setAvatarUrl(avatarUrl.trim());
                updated = true;
            }
            if ((existingUser.getFullName() == null || existingUser.getFullName().isBlank())
                    && fullName != null && !fullName.isBlank()) {
                existingUser.setFullName(fullName.trim());
                updated = true;
            }
            if (updated) {
                existingUser = userRepository.save(existingUser);
            }

            log.info("Existing user signed in via Google: id={}, email={}, role={}",
                    existingUser.getId(), existingUser.getEmail(), existingUser.getRole());
            return new GoogleAuthResult(existingUser, false);
        }

        // Tài khoản chưa tồn tại -> Tự động đăng ký mới với Role.USER & UserStatus.ACTIVE
        String resolvedName = (fullName != null && !fullName.isBlank())
                ? fullName.trim()
                : extractDisplayNameFromEmail(normalizedEmail);
        if (resolvedName.length() > 100) {
            resolvedName = resolvedName.substring(0, 100);
        }

        String resolvedAvatar = (avatarUrl != null && !avatarUrl.isBlank()) ? avatarUrl.trim() : null;

        User newUser = User.builder()
                .fullName(resolvedName)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode("GOOGLE_OAUTH2_" + UUID.randomUUID()))
                .avatarUrl(resolvedAvatar)
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("Auto-registered new user via Google OAuth2: id={}, email={}, googleId={}",
                savedUser.getId(), savedUser.getEmail(), googleId);

        return new GoogleAuthResult(savedUser, true);
    }

    private String extractDisplayNameFromEmail(String email) {
        int atIndex = email.indexOf('@');
        String localPart = atIndex > 0 ? email.substring(0, atIndex) : email;
        String cleaned = localPart.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) {
            return "Thành viên Google";
        }
        String[] words = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) {
                    sb.append(w.substring(1));
                }
            }
        }
        return sb.toString();
    }
}
