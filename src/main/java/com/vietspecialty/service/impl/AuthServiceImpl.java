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
}

