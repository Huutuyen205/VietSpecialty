package com.vietspecialty.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestPropertySource(properties = {
        "app.google.client-id=test-google-client-id",
        "app.google.client-secret=test-google-client-secret",
        "app.google.redirect-uri=http://localhost:8080/auth/google/callback"
})
class GoogleOAuthRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /auth/google?mode=login khi đã cấu hình OAuth redirect 302 tới Google Authorization endpoint")
    void testGoogleOAuthLoginRedirect() throws Exception {
        mockMvc.perform(get("/auth/google").param("mode", "login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth"),
                        containsString("client_id=test-google-client-id"),
                        containsString("response_type=code"),
                        containsString("state=login")
                )));
    }

    @Test
    @DisplayName("GET /auth/google?mode=register&popup=true lưu session GOOGLE_AUTH_POPUP và redirect 302 với state=register")
    void testGoogleOAuthRegisterPopupRedirect() throws Exception {
        mockMvc.perform(get("/auth/google").param("mode", "register").param("popup", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth"),
                        containsString("client_id=test-google-client-id"),
                        containsString("response_type=code"),
                        containsString("state=register")
                )))
                .andExpect(request().sessionAttribute("GOOGLE_AUTH_POPUP", Boolean.TRUE));
    }

    @Test
    @DisplayName("GET /auth/google?mode=login&local=true trả về 200 OK và view auth/google-confirm")
    void testForceLocalChooserWhenOAuthConfigured() throws Exception {
        mockMvc.perform(get("/auth/google").param("mode", "login").param("local", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/google-confirm"));
    }

    @Test
    @DisplayName("GET /auth/google/callback?error=access_denied chuyển hướng an toàn về /login?error=...")
    void testGoogleOAuthCallbackAccessDeniedRedirectsGracefully() throws Exception {
        mockMvc.perform(get("/auth/google/callback").param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/login?error=")));

        mockMvc.perform(get("/auth/google/callback").param("error", "access_denied").param("popup", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/google-popup-complete"))
                .andExpect(model().attribute("success", false))
                .andExpect(model().attribute("errorUrl", startsWith("/login?error=")));
    }
}
