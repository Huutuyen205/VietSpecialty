package com.vietspecialty.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {

        String errorMessage;
        if (exception instanceof LockedException || exception instanceof DisabledException) {
            errorMessage = "Tài khoản đã bị khóa, vui lòng liên hệ hỗ trợ";
        } else if (exception instanceof BadCredentialsException) {
            errorMessage = "Email hoặc mật khẩu không chính xác";
        } else {
            errorMessage = "Đăng nhập không thành công, vui lòng thử lại";
        }

        log.warn("Login failed for IP {}: {}", request.getRemoteAddr(), exception.getMessage());
        String encodedMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        String email = request.getParameter("email");
        StringBuilder failureUrl = new StringBuilder("/login?error=").append(encodedMessage);
        if (email != null && !email.isBlank()) {
            failureUrl.append("&email=").append(URLEncoder.encode(email.trim(), StandardCharsets.UTF_8));
        }
        setDefaultFailureUrl(failureUrl.toString());
        super.onAuthenticationFailure(request, response, exception);
    }
}

