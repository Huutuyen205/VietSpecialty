package com.vietspecialty.security;

import com.vietspecialty.domain.entity.ChatConversation;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.repository.ChatConversationRepository;
import com.vietspecialty.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final ChatConversationRepository chatConversationRepository;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            log.info("User logged in successfully: id={}, email={}, role={}",
                    userDetails.getId(), userDetails.getEmail(), userDetails.getRole());

            // Gắn user_id vào phiên chat ẩn danh nếu có trong session
            HttpSession session = request.getSession(false);
            if (session != null) {
                String chatKey = (String) session.getAttribute("VS_CHAT_KEY");
                if (chatKey != null) {
                    chatConversationRepository.findByChatKey(chatKey).ifPresent(conv -> {
                        if (conv.getUser() == null) {
                            userRepository.findById(userDetails.getId()).ifPresent(user -> {
                                conv.setUser(user);
                                chatConversationRepository.save(conv);
                                log.debug("Attached user {} to chat conversation {}", user.getId(), chatKey);
                            });
                        }
                    });
                }
            }

            // Kiểm tra SavedRequest (vd: người dùng đang checkout thì bị bắt login)
            SavedRequest savedRequest = requestCache.getRequest(request, response);
            if (savedRequest != null) {
                String targetUrl = savedRequest.getRedirectUrl();
                clearAuthenticationAttributes(request);
                getRedirectStrategy().sendRedirect(request, response, targetUrl);
                return;
            }

            // Nếu không có SavedRequest, điều hướng theo vai trò người dùng
            clearAuthenticationAttributes(request);
            Role role = userDetails.getRole();
            if (role == Role.ADMIN) {
                getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
            } else if (role == Role.SELLER) {
                getRedirectStrategy().sendRedirect(request, response, "/seller/dashboard");
            } else {
                getRedirectStrategy().sendRedirect(request, response, "/");
            }
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}

