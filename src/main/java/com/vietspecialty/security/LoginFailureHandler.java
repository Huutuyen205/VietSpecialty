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
        setDefaultFailureUrl("/login?error=" + encodedMessage);
        super.onAuthenticationFailure(request, response, exception);
    }
}

