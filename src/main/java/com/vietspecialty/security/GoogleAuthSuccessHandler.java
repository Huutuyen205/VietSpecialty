package com.vietspecialty.security;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.repository.ChatConversationRepository;
import com.vietspecialty.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

/**
 * Handler chuyên trách thiết lập phiên đăng nhập Spring Security (SecurityContext + HttpSession + SessionRegistry)
 * và xác định URL điều hướng thông minh sau khi xác thực bằng Google thành công.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleAuthSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final SessionRegistry sessionRegistry;
    private final ChatConversationRepository chatConversationRepository;
    private final UserRepository userRepository;

    /**
     * Nạp CustomUserDetails vào SecurityContextHolder và HttpSession, đồng thời trả về URL đích theo Role.
     */
    public String establishSessionAndDetermineTargetUrl(
            User user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        sessionRegistry.registerNewSession(session.getId(), userDetails);

        // Liên kết lịch sử trò chuyện Chatbot AI ẩn danh trong session (nếu có) với tài khoản vừa đăng nhập
        String chatKey = (String) session.getAttribute("VS_CHAT_KEY");
        if (chatKey != null) {
            chatConversationRepository.findByChatKey(chatKey).ifPresent(conv -> {
                if (conv.getUser() == null) {
                    userRepository.findById(userDetails.getId()).ifPresent(u -> {
                        conv.setUser(u);
                        chatConversationRepository.save(conv);
                        log.debug("Attached Google-authenticated user {} to chat conversation {}", u.getId(), chatKey);
                    });
                }
            });
        }

        Role role = userDetails.getRole();
        if (role == Role.ADMIN) {
            requestCache.removeRequest(request, response);
            return "/admin/dashboard";
        }
        if (role == Role.SELLER) {
            requestCache.removeRequest(request, response);
            return "/seller/dashboard";
        }

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String targetUrl = savedRequest.getRedirectUrl();
            if (targetUrl != null && !targetUrl.contains("/api/") && !targetUrl.contains("/login")
                    && !targetUrl.contains("/register") && !targetUrl.contains("/auth/google")
                    && !targetUrl.contains("/error")) {
                requestCache.removeRequest(request, response);
                return targetUrl;
            }
        }

        return "/";
    }
}
