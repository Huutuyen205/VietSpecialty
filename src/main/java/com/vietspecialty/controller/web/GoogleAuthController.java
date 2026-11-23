package com.vietspecialty.controller.web;

import com.vietspecialty.config.AppProperties;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.security.GoogleAuthSuccessHandler;
import com.vietspecialty.security.GoogleOAuth2UserService;
import com.vietspecialty.security.SecurityUtils;
import com.vietspecialty.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Controller xử lý luồng Đăng ký & Đăng nhập bằng Google (Hỗ trợ 2 chế độ:
 * Real Google OAuth2 / Google Identity Services + Local Google Account Chooser).
 */
@Slf4j
@Controller
@RequestMapping("/auth/google")
@RequiredArgsConstructor
public class GoogleAuthController {

    private final AuthService authService;
    private final GoogleOAuth2UserService googleOAuth2UserService;
    private final GoogleAuthSuccessHandler googleAuthSuccessHandler;
    private final AppProperties appProperties;

    @GetMapping
    public String initiateGoogleAuth(
            @RequestParam(value = "mode", defaultValue = "login") String mode,
            @RequestParam(value = "local", defaultValue = "false") boolean forceLocal,
            Model model
    ) {
        if (SecurityUtils.isAuthenticated()) {
            return "redirect:/";
        }

        // Chế độ 1: Khi đã cấu hình GOOGLE_CLIENT_ID & GOOGLE_CLIENT_SECRET thật -> Redirect sang Google OAuth2
        if (!forceLocal && googleOAuth2UserService.isRealOAuthConfigured()) {
            String authUrl = googleOAuth2UserService.buildAuthorizationUrl(mode);
            log.info("Redirecting user to Google OAuth2 authorization screen (mode={})", mode);
            return "redirect:" + authUrl;
        }

        // Chế độ 2: Khi chạy Local/Demo chưa cấu hình Google Cloud OAuth2 -> Hiển thị Google Account Chooser
        return renderGoogleChooserPage(mode, model);
    }

    @GetMapping("/choose")
    public String googleAccountChooser(
            @RequestParam(value = "mode", defaultValue = "login") String mode,
            Model model
    ) {
        if (SecurityUtils.isAuthenticated()) {
            return "redirect:/";
        }
        return renderGoogleChooserPage(mode, model);
    }

    @GetMapping("/callback")
    public String handleGoogleOAuthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) {
        if (error != null && !error.isBlank()) {
            log.warn("Google OAuth2 callback returned error: {}", error);
            return redirectLoginError("Đăng nhập Google đã bị hủy hoặc từ chối quyền truy cập");
        }

        if (code == null || code.isBlank()) {
            return redirectLoginError("Không nhận được mã xác thực từ Google");
        }

        try {
            GoogleOAuth2UserService.GoogleUserProfile profile = googleOAuth2UserService.exchangeCodeForProfile(code);
            return completeGoogleAuthentication(
                    profile.email(),
                    profile.fullName(),
                    profile.googleId(),
                    profile.avatarUrl(),
                    request,
                    response,
                    redirectAttributes
            );
        } catch (BusinessException ex) {
            return handleAuthBusinessException(ex);
        } catch (Exception ex) {
            log.error("Unexpected error in Google OAuth2 callback", ex);
            return redirectLoginError("Xác thực tài khoản Google không thành công, vui lòng thử lại");
        }
    }

    @PostMapping("/callback")
    public String handleGooglePostCallback(
            @RequestParam(value = "credential", required = false) String credential,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "googleId", required = false) String googleId,
            @RequestParam(value = "avatarUrl", required = false) String avatarUrl,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (credential != null && !credential.isBlank()) {
                GoogleOAuth2UserService.GoogleUserProfile profile = googleOAuth2UserService.parseIdTokenPayload(credential);
                return completeGoogleAuthentication(
                        profile.email(),
                        profile.fullName(),
                        profile.googleId(),
                        profile.avatarUrl(),
                        request,
                        response,
                        redirectAttributes
                );
            }

            return completeGoogleAuthentication(email, fullName, googleId, avatarUrl, request, response, redirectAttributes);
        } catch (BusinessException ex) {
            return handleAuthBusinessException(ex);
        } catch (Exception ex) {
            log.error("Unexpected error in Google authentication submit", ex);
            return redirectLoginError("Xác thực tài khoản Google không thành công, vui lòng thử lại");
        }
    }

    @PostMapping("/confirm")
    public String confirmLocalGoogleAccount(
            @RequestParam("email") String email,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "googleId", required = false) String googleId,
            @RequestParam(value = "avatarUrl", required = false) String avatarUrl,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) {
        try {
            return completeGoogleAuthentication(email, fullName, googleId, avatarUrl, request, response, redirectAttributes);
        } catch (BusinessException ex) {
            return handleAuthBusinessException(ex);
        } catch (Exception ex) {
            log.error("Unexpected error confirming Google account", ex);
            return redirectLoginError("Xác thực tài khoản Google không thành công, vui lòng thử lại");
        }
    }

    private String completeGoogleAuthentication(
            String email,
            String fullName,
            String googleId,
            String avatarUrl,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) {
        AuthService.GoogleAuthResult authResult = authService.authenticateWithGoogleWithResult(
                email, fullName, googleId, avatarUrl
        );

        String targetUrl = googleAuthSuccessHandler.establishSessionAndDetermineTargetUrl(
                authResult.user(), request, response
        );

        if (authResult.newlyRegistered()) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đăng ký và đăng nhập bằng tài khoản Google thành công!");
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đăng nhập bằng tài khoản Google thành công!");
        }

        return "redirect:" + targetUrl;
    }

    private String renderGoogleChooserPage(String mode, Model model) {
        String normalizedMode = "register".equalsIgnoreCase(mode) ? "register" : "login";
        model.addAttribute("mode", normalizedMode);
        model.addAttribute("googleClientId",
                appProperties.getGoogle() != null ? appProperties.getGoogle().getClientId() : "");
        return "auth/google-confirm";
    }

    private String handleAuthBusinessException(BusinessException ex) {
        if (ex.getErrorCode() == ErrorCode.ACCOUNT_LOCKED) {
            return redirectLoginError("Tài khoản đã bị khóa, vui lòng liên hệ ban quản trị để được hỗ trợ");
        }
        return redirectLoginError(ex.getMessage());
    }

    private String redirectLoginError(String errorMessage) {
        String encoded = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        return "redirect:/login?error=" + encoded;
    }
}
