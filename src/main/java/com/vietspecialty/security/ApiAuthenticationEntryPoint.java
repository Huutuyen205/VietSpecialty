package com.vietspecialty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {

        String uri = request.getRequestURI();
        log.warn("Unauthorized access attempt to {}: {}", uri, authException.getMessage());

        if (uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ApiResponse<Void> body = ApiResponse.error("Vui lòng đăng nhập để thực hiện thao tác này");
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } else {
            String redirectUrl = "/login?redirect=" + URLEncoder.encode(uri, StandardCharsets.UTF_8);
            redirectStrategy.sendRedirect(request, response, redirectUrl);
        }
    }
}

