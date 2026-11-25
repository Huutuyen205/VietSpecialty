package com.vietspecialty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SELLER").implies("USER")
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(new NegatedRequestMatcher(new AntPathRequestMatcher("/api/**")));

        http
                .requestCache(cache -> cache.requestCache(requestCache))
                // CSRF bảo vệ toàn diện, ngoại trừ Webhook IPN từ VNPay và Chatbot API
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/payment/vnpay/ipn", "/api/chat/**", "/api/chatbot/**"))
                // Phân quyền truy cập theo URL
                .authorizeHttpRequests(auth -> auth
                        // Tài nguyên công khai & Trang xem sản phẩm
                        .requestMatchers(
                                "/",
                                "/explore",
                                "/explore/**",
                                "/products/**",
                                "/shops/**",
                                "/regions/**",
                                "/categories/**",
                                "/search/**",
                                "/login",
                                "/register",
                                "/auth/google/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/vendor/**",
                                "/uploads/**",
                                "/api/explore/**",
                                "/api/products/**",
                                "/api/chat/**",
                                "/api/chatbot/**",
                                "/api/locations/**",
                                "/payment/vnpay/return",
                                "/api/payment/vnpay/ipn",
                                "/error",
                                "/error/**")
                        .permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reviews/product/**").permitAll()

                        // Khu vực Quản trị viên
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")

                        // Khu vực Kênh người bán
                        .requestMatchers("/seller/**", "/api/seller/**").hasRole("SELLER")

                        // Đăng ký gian hàng cho phép cả USER, SELLER và ADMIN để điều hướng thông minh theo vai trò
                        .requestMatchers("/account/shop-registration", "/account/shop-registration/**").hasAnyRole("USER", "SELLER", "ADMIN")

                        // Thông báo cá nhân cho phép mọi vai trò đã đăng nhập (bao gồm ADMIN)
                        .requestMatchers("/api/notifications/**").hasAnyRole("USER", "SELLER", "ADMIN")

                        // Khu vực Người mua / Thành viên
                        .requestMatchers(
                                "/account/**",
                                "/cart/**",
                                "/checkout/**",
                                "/orders/**",
                                "/api/cart/**",
                                "/api/checkout/**",
                                "/api/orders/**",
                                "/api/reviews/**")
                        .hasRole("USER")

                        // Mọi yêu cầu còn lại (bao gồm các trang không tồn tại) cho phép hiển thị trang
                        // 404 thân thiện
                        .anyRequest().permitAll())
                // Form Login (Session-based)
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                // Logout
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                // Quản lý phiên làm việc & SessionRegistry
                .sessionManagement(session -> session
                        .maximumSessions(10)
                        .sessionRegistry(sessionRegistry())
                        .expiredSessionStrategy(event -> {
                            HttpServletRequest req = event.getRequest();
                            HttpServletResponse res = event.getResponse();
                            String uri = req.getRequestURI();
                            if (uri != null && uri.startsWith("/api/")) {
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                                ApiResponse<Void> body = ApiResponse.error("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
                                res.getWriter().write(objectMapper.writeValueAsString(body));
                            } else {
                                res.sendRedirect(req.getContextPath() + "/login?expired");
                            }
                        }))
                // Xử lý lỗi xác thực & phân quyền (401 JSON cho /api/**, 403 phân nhánh)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler));

        return http.build();
    }
}
