package com.vietspecialty.auth;

import com.vietspecialty.dto.request.RegisterRequest;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Đăng ký tài khoản mới thành công")
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest(
                "Nguyễn Văn Test",
                "test_register_unique@vietspecialty.vn",
                "0912345678",
                "Secret123@",
                "Secret123@"
        );

        assertDoesNotThrow(() -> authService.register(request));

        assertTrue(userRepository.findByEmail("test_register_unique@vietspecialty.vn").isPresent());
    }

    @Test
    @DisplayName("Đăng ký trùng email phải quăng BusinessException EMAIL_ALREADY_EXISTS")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "Trùng Email",
                "admin@vietspecialty.vn", // Đã có từ DataInitializer
                "0988776655",
                "Secret123@",
                "Secret123@"
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }
}
