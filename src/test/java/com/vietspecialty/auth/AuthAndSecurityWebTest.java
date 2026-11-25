package com.vietspecialty.auth;

import com.vietspecialty.domain.entity.ChatConversation;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.exception.GlobalWebExceptionHandler;
import com.vietspecialty.repository.ChatConversationRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.security.SessionInvalidator;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AuthAndSecurityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionInvalidator sessionInvalidator;

    @Autowired
    private EntityManager entityManager;

    private CustomUserDetails detailsOf(String email) {
        User u = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return new CustomUserDetails(u);
    }

    @Test
    @DisplayName("POST /login chuyển hướng đúng theo từng Role (USER -> /, SELLER -> /seller/dashboard, ADMIN -> /admin/dashboard) và chặn tài khoản LOCKED")
    void testFormLoginRoleRedirectsAndLockedUser() throws Exception {
        // USER -> /
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "buyer@vietspecialty.vn")
                        .param("password", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("buyer@vietspecialty.vn").withRoles("USER"));

        // SELLER -> /seller/dashboard
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "seller_bac@vietspecialty.vn")
                        .param("password", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/dashboard"))
                .andExpect(authenticated().withUsername("seller_bac@vietspecialty.vn").withRoles("SELLER"));

        // ADMIN -> /admin/dashboard
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "admin@vietspecialty.vn")
                        .param("password", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andExpect(authenticated().withUsername("admin@vietspecialty.vn").withRoles("ADMIN"));

        // LOCKED user -> /login?error=... & unauthenticated
        String lockedEmail = "locked_login_" + UUID.randomUUID().toString().substring(0, 8) + "@vietspecialty.vn";
        userRepository.save(User.builder()
                .fullName("Locked Login User")
                .email(lockedEmail)
                .passwordHash(passwordEncoder.encode("Password123@"))
                .role(Role.USER)
                .status(UserStatus.LOCKED)
                .build());

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", lockedEmail)
                        .param("password", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login?error=")))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("LoginSuccessHandler tự động gắn user vào ChatConversation ẩn danh theo session VS_CHAT_KEY")
    void testLoginSuccessHandlerAttachesAnonymousChatConversation() throws Exception {
        String chatKey = "vs-chat-" + UUID.randomUUID().toString().substring(0, 12);
        ChatConversation anonymousConv = ChatConversation.builder()
                .chatKey(chatKey)
                .user(null)
                .lastMessageAt(LocalDateTime.now())
                .build();
        chatConversationRepository.saveAndFlush(anonymousConv);
        entityManager.clear();

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .sessionAttr("VS_CHAT_KEY", chatKey)
                        .param("email", "buyer@vietspecialty.vn")
                        .param("password", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("buyer@vietspecialty.vn"));

        entityManager.flush();
        entityManager.clear();
        ChatConversation linked = chatConversationRepository.findByChatKey(chatKey).orElseThrow();
        assertNotNull(linked.getUser(), "ChatConversation ẩn danh phải được gắn với user sau khi đăng nhập");
        assertEquals("buyer@vietspecialty.vn", linked.getUser().getEmail());
    }

    @Test
    @DisplayName("POST /register kiểm tra mật khẩu không khớp và validation field errors (phone, password ngắn, fullName rỗng)")
    void testRegisterValidationErrors() throws Exception {
        // Password mismatch
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "Nguyễn Văn A")
                        .param("email", "mismatch_" + UUID.randomUUID().toString().substring(0, 6) + "@vietspecialty.vn")
                        .param("phone", "0912345678")
                        .param("password", "Password123@")
                        .param("confirmPassword", "Different123@"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("registerRequest", "confirmPassword"));

        // Invalid phone, short password, blank fullName
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "")
                        .param("email", "invalid_" + UUID.randomUUID().toString().substring(0, 6) + "@vietspecialty.vn")
                        .param("phone", "12345")
                        .param("password", "123")
                        .param("confirmPassword", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("registerRequest", "fullName", "phone", "password"));
    }

    @Test
    @DisplayName("Thiếu CSRF token bị chặn 403 trên POST /login, POST /register và trả 403 JSON trên /api/cart/items")
    void testCsrfForbiddenWhenTokenMissing() throws Exception {
        mockMvc.perform(post("/login")
                        .param("email", "buyer@vietspecialty.vn")
                        .param("password", "Password123@"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/register")
                        .param("fullName", "No Csrf User")
                        .param("email", "nocsrf@vietspecialty.vn")
                        .param("phone", "0912345678")
                        .param("password", "Password123@")
                        .param("confirmPassword", "Password123@"))
                .andExpect(status().isForbidden());

        CustomUserDetails buyer = detailsOf("buyer@vietspecialty.vn");
        mockMvc.perform(post("/api/cart/items")
                        .with(user(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("RBAC /api/admin/** trả về 403 JSON cho USER/SELLER và 401 JSON cho guest")
    void testAdminApiRbacJsonResponses() throws Exception {
        // Guest -> 401 JSON
        mockMvc.perform(get("/api/admin/analytics/revenue"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));

        // USER -> 403 JSON
        CustomUserDetails buyer = detailsOf("buyer@vietspecialty.vn");
        mockMvc.perform(get("/api/admin/analytics/revenue").with(user(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));

        // SELLER -> 403 JSON
        CustomUserDetails seller = detailsOf("seller_bac@vietspecialty.vn");
        mockMvc.perform(get("/api/admin/analytics/revenue").with(user(seller)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GlobalWebExceptionHandler xử lý 404, 400 BusinessException, 403 và 500 đúng view & status")
    void testGlobalWebExceptionHandler() throws Exception {
        // Unmapped route -> 404 Not Found
        mockMvc.perform(get("/non-existent-page-404"))
                .andExpect(status().isNotFound());

        // DEF-24: Storefront missing product -> 404 error/404 with errorCode PROD_001
        mockMvc.perform(get("/products/non-existent-slug-999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("errorCode", "PROD_001"));

        // USER accessing /admin/dashboard -> 403
        CustomUserDetails buyer = detailsOf("buyer@vietspecialty.vn");
        mockMvc.perform(get("/admin/dashboard").with(user(buyer)))
                .andExpect(status().isForbidden());

        // Standalone MockMvc verifying GlobalWebExceptionHandler for 404, 403, and 500 views
        MockMvc standalone = MockMvcBuilders
                .standaloneSetup(new ThrowingWebTestController())
                .setControllerAdvice(new GlobalWebExceptionHandler())
                .build();
        standalone.perform(get("/test-throw-404"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(model().attributeExists("requestUri"));
        standalone.perform(get("/test-throw-403"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"))
                .andExpect(model().attributeExists("requestUri"));
        standalone.perform(get("/test-throw-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"))
                .andExpect(model().attributeExists("errorMessage", "requestUri"));
    }

    @Test
    @DisplayName("GlobalApiExceptionHandler trả về 400 validation errors JSON và 404 BusinessException JSON")
    void testGlobalApiExceptionHandler() throws Exception {
        CustomUserDetails buyer = detailsOf("buyer@vietspecialty.vn");

        // MethodArgumentNotValidException -> 400 JSON with $.errors
        mockMvc.perform(post("/api/cart/items")
                        .with(user(buyer))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.errors.productId").exists());

        // BusinessException CART_ITEM_NOT_FOUND -> 404 JSON
        mockMvc.perform(delete("/api/cart/items/999999")
                        .with(user(buyer))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DEF-11: ADMIN truy cập /api/notifications/** trả về 200 JSON trong khi vẫn bị chặn 403 trên /cart và /api/cart/count")
    void testAdminNotificationsAllowedWhileCartRemainsForbidden() throws Exception {
        CustomUserDetails admin = detailsOf("admin@vietspecialty.vn");

        mockMvc.perform(get("/api/notifications").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread-count").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unreadCount").exists());

        mockMvc.perform(post("/api/notifications/read-all")
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // RoleHierarchy must keep SELLER -> USER only so ADMIN gets 403 on cart & seller endpoints
        mockMvc.perform(get("/cart").with(user(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/cart/count").with(user(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/seller/dashboard").with(user(admin)))
                .andExpect(status().isForbidden());

        // Header fragment sets data-auth="false" (cart) and data-notif-auth="true" (notifications) for ADMIN
        mockMvc.perform(get("/").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-auth=\"false\"")))
                .andExpect(content().string(containsString("data-notif-auth=\"true\"")));
    }

    @Test
    @DisplayName("DEF-22: Phiên đăng nhập bị vô hiệu hóa trả về 302 /login?expired cho web và 401 JSON cho /api/**")
    void testExpiredSessionStrategyForWebAndApi() throws Exception {
        User buyerUser = userRepository.findByEmailIgnoreCase("buyer@vietspecialty.vn").orElseThrow();

        // 1. Web expired session -> 302 /login?expired
        MvcResult webLoginRes = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", buyerUser.getEmail())
                        .param("password", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        MockHttpSession webSession = (MockHttpSession) webLoginRes.getRequest().getSession(false);
        assertNotNull(webSession);

        sessionInvalidator.invalidateUserSessions(buyerUser.getId());

        mockMvc.perform(get("/account/profile").session(webSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        // 2. API expired session -> 401 JSON
        MvcResult apiLoginRes = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", buyerUser.getEmail())
                        .param("password", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        MockHttpSession apiSession = (MockHttpSession) apiLoginRes.getRequest().getSession(false);
        assertNotNull(apiSession);

        sessionInvalidator.invalidateUserSessions(buyerUser.getId());

        mockMvc.perform(get("/api/cart/count").session(apiSession))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));

        // 3. GET /login?expired renders user-friendly expiration message
        mockMvc.perform(get("/login").param("expired", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Controller
    static class ThrowingWebTestController {
        @GetMapping("/test-throw-404")
        public String throwNotFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/non-existent-page-404");
        }

        @GetMapping("/test-throw-403")
        public String throwAccessDenied() {
            throw new AccessDeniedException("Simulated 403 access denied");
        }

        @GetMapping("/test-throw-500")
        public String throwUnexpected() {
            throw new RuntimeException("Simulated unexpected 500 error");
        }
    }
}
