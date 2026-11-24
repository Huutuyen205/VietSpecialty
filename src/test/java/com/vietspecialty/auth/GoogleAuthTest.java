package com.vietspecialty.auth;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestPropertySource(properties = {
        "app.google.client-id=",
        "app.google.client-secret="
})
class GoogleAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Trang /login và /register hiển thị nút Đăng nhập & Đăng ký bằng Google đúng chuẩn")
    void testLoginAndRegisterPagesShowGoogleButtons() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tiếp tục với Google")))
                .andExpect(content().string(containsString("/auth/google")));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng ký nhanh bằng Google")))
                .andExpect(content().string(containsString("/auth/google")));
    }

    @Test
    @DisplayName("Hiển thị trang chọn tài khoản Google /auth/google và /auth/google/choose hoạt động tốt")
    void testGoogleAccountChooserPageRenders() throws Exception {
        mockMvc.perform(get("/auth/google").param("mode", "register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng ký nhanh bằng Google")))
                .andExpect(content().string(containsString("/auth/google/confirm")));

        mockMvc.perform(get("/auth/google/choose").param("mode", "login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chọn tài khoản Google")));
    }

    @Test
    @DisplayName("Đăng ký mới qua Google thành công: tạo User trong DB, set SecurityContext và redirect về /")
    void testGoogleSignUpNewUserSuccess() throws Exception {
        String newEmail = "new.google.buyer@gmail.com";
        assertFalse(userRepository.existsByEmailIgnoreCase(newEmail));

        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", "  NEW.Google.Buyer@gmail.com ")
                        .param("fullName", "Lê Hoàng Nam")
                        .param("googleId", "google-sub-99999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage",
                        "Đăng ký và đăng nhập bằng tài khoản Google thành công!"))
                .andExpect(authenticated().withUsername(newEmail).withRoles("USER"));

        Optional<User> createdUserOpt = userRepository.findByEmailIgnoreCase(newEmail);
        assertTrue(createdUserOpt.isPresent());
        User createdUser = createdUserOpt.get();
        assertEquals("Lê Hoàng Nam", createdUser.getFullName());
        assertEquals(Role.USER, createdUser.getRole());
        assertEquals(UserStatus.ACTIVE, createdUser.getStatus());
        assertNotNull(createdUser.getPasswordHash());
        assertTrue(createdUser.getPasswordHash().startsWith("$2"));
    }

    @Test
    @DisplayName("Đăng nhập lại bằng tài khoản Google đã tồn tại thành công và điều hướng thông minh theo Role")
    void testGoogleSignInExistingUserAndRoleRedirect() throws Exception {
        // 1. User thường đã tồn tại -> redirect /
        String existingEmail = "existing.google.user@gmail.com";
        authService.authenticateWithGoogle(existingEmail, "Phạm Minh Tuấn", "g-123", null);

        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", existingEmail)
                        .param("fullName", "Phạm Minh Tuấn"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage",
                        "Đăng nhập bằng tài khoản Google thành công!"))
                .andExpect(authenticated().withUsername(existingEmail).withRoles("USER"));

        // 2. Tài khoản SELLER đăng nhập qua Google -> redirect /seller/dashboard
        User sellerUser = userRepository.findByEmailIgnoreCase("seller_bac@vietspecialty.vn")
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName("Seller Bắc")
                        .email("seller_bac@vietspecialty.vn")
                        .passwordHash("$2a$10$dummyhashforgoogleauthtest1234567890123456")
                        .role(Role.SELLER)
                        .status(UserStatus.ACTIVE)
                        .build()));

        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", sellerUser.getEmail())
                        .param("fullName", sellerUser.getFullName()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/dashboard"))
                .andExpect(authenticated().withUsername(sellerUser.getEmail()).withRoles("SELLER"));
    }

    @Test
    @DisplayName("Tài khoản bị khóa (LOCKED) đăng nhập bằng Google bị chặn và báo lỗi rõ ràng trên /login")
    void testGoogleSignInLockedUserBlocked() throws Exception {
        String lockedEmail = "locked.google.user@gmail.com";
        userRepository.save(User.builder()
                .fullName("Tài Khoản Bị Khóa")
                .email(lockedEmail)
                .passwordHash("$2a$10$dummyhashforlockeduser1234567890123456789")
                .role(Role.USER)
                .status(UserStatus.LOCKED)
                .build());

        // Kiểm tra ở tầng Service
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.authenticateWithGoogle(lockedEmail, "Tài Khoản Bị Khóa", "g-locked", null));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());

        // Kiểm tra ở tầng Web Controller
        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", lockedEmail)
                        .param("fullName", "Tài Khoản Bị Khóa"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login?error=")))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Xác thực qua Google Identity Services (GIS ID Token credential) tại POST /auth/google/callback thành công")
    void testGoogleIdTokenCredentialCallback() throws Exception {
        String payloadJson = "{\"sub\":\"gis-98765\",\"email\":\"gis.token.user@gmail.com\",\"name\":\" Đặng Quốc Bảo \",\"picture\":\"https://lh3.googleusercontent.com/a/default-user\"}";
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String fakeJwtCredential = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." + encodedPayload + ".signature";

        mockMvc.perform(post("/auth/google/callback")
                        .with(csrf())
                        .param("credential", fakeJwtCredential))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("gis.token.user@gmail.com").withRoles("USER"));

        User saved = userRepository.findByEmailIgnoreCase("gis.token.user@gmail.com").orElseThrow();
        assertEquals("Đặng Quốc Bảo", saved.getFullName());
        assertEquals("https://lh3.googleusercontent.com/a/default-user", saved.getAvatarUrl());
    }

    @Test
    @DisplayName("GET /auth/google?mode=login&popup=true trả về trang popup độc lập auth/google-popup")
    void testGooglePopupPageRendersStandalone() throws Exception {
        mockMvc.perform(get("/auth/google").param("mode", "login").param("popup", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/google-popup"))
                .andExpect(content().string(containsString("Đăng nhập bằng Google")))
                .andExpect(content().string(containsString("Chọn tài khoản")))
                .andExpect(content().string(containsString("Xác thực đăng nhập vào VietSpecialty")))
                .andExpect(content().string(containsString("/auth/google/confirm")));

        mockMvc.perform(get("/auth/google").param("mode", "register").param("popup", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/google-popup"))
                .andExpect(content().string(containsString("Đăng ký bằng Google")));
    }

    @Test
    @DisplayName("POST /auth/google/confirm với popup=true trả về auth/google-popup-complete và tạo session thành công")
    void testGooglePopupConfirmSuccessAndLockedError() throws Exception {
        String popupEmail = "popup.google.user@gmail.com";

        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", popupEmail)
                        .param("fullName", "Nguyễn Minh Châu")
                        .param("googleId", "google-popup-777")
                        .param("popup", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/google-popup-complete"))
                .andExpect(model().attribute("success", true))
                .andExpect(model().attribute("redirectUrl", "/"))
                .andExpect(content().string(containsString("GOOGLE_AUTH_COMPLETE")))
                .andExpect(authenticated().withUsername(popupEmail).withRoles("USER"));

        assertTrue(userRepository.existsByEmailIgnoreCase(popupEmail));

        String lockedPopupEmail = "locked.popup.user@gmail.com";
        userRepository.save(User.builder()
                .fullName("Locked Popup User")
                .email(lockedPopupEmail)
                .passwordHash("$2a$10$dummyhashforlockedpopupuser1234567890123")
                .role(Role.USER)
                .status(UserStatus.LOCKED)
                .build());

        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", lockedPopupEmail)
                        .param("fullName", "Locked Popup User")
                        .param("popup", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/google-popup-complete"))
                .andExpect(model().attribute("success", false))
                .andExpect(model().attribute("errorUrl", containsString("/login?error=")))
                .andExpect(unauthenticated());
    }
}
