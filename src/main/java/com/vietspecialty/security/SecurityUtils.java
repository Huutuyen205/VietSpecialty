package com.vietspecialty.security;

import com.vietspecialty.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityUtils {

    private static UserRepository userRepository;

    public SecurityUtils(UserRepository userRepository) {
        SecurityUtils.userRepository = userRepository;
    }

    public static Optional<CustomUserDetails> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                return Optional.of(userDetails);
            }
            if (userRepository != null && auth.getName() != null) {
                return userRepository.findByEmailIgnoreCase(auth.getName())
                        .map(CustomUserDetails::new);
            }
        }
        return Optional.empty();
    }

    public static Optional<Long> getUserId() {
        return get().map(CustomUserDetails::getId);
    }

    public static boolean isAuthenticated() {
        return get().isPresent();
    }
}
