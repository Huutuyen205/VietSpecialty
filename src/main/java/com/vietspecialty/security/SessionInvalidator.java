package com.vietspecialty.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionInvalidator {

    private final SessionRegistry sessionRegistry;

    /**
     * Hủy toàn bộ phiên làm việc (Session) của một người dùng theo userId.
     * Sử dụng khi tài khoản bị khóa hoặc khi vai trò thay đổi (nâng cấp thành SELLER).
     */
    public void invalidateUserSessions(Long userId) {
        if (userId == null) return;

        List<Object> allPrincipals = sessionRegistry.getAllPrincipals();
        for (Object principal : allPrincipals) {
            if (principal instanceof CustomUserDetails userDetails && userId.equals(userDetails.getId())) {
                List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                for (SessionInformation sessionInfo : sessions) {
                    log.info("Expiring session {} for userId={}", sessionInfo.getSessionId(), userId);
                    sessionInfo.expireNow();
                }
            }
        }
    }
}

