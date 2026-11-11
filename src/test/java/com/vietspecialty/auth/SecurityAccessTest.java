package com.vietspecialty.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Khách vãng lai truy cập trang chủ thành công (200 OK)")
    void testPublicHomeAccess() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Khách vãng lai truy cập trang đăng nhập thành công (200 OK)")
    void testLoginPageAccess() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Khách vãng lai vào /account/profile phải bị chuyển hướng đến /login")
    void testGuestAccessProfileRedirectToLogin() throws Exception {
        mockMvc.perform(get("/account/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @WithMockUser(username = "buyer@vietspecialty.vn", roles = {"USER"})
    @DisplayName("Người dùng đã đăng nhập truy cập /account/addresses thành công (200 OK)")
    void testAuthenticatedUserProfileAccess() throws Exception {
        mockMvc.perform(get("/account/addresses"))
                .andExpect(status().isOk());
    }
}
