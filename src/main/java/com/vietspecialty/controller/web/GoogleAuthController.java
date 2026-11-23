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
import jakarta.servlet.http.HttpSession;
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
 * Real Google OAuth2 / Google Identity Services + Local Google Account Chooser,
 * đồng thời hỗ trợ mở trong cửa sổ Popup độc lập với popup=true).
 */
@Slf4j
@Controller
@RequestMapping("/auth/google")
@RequiredArgsConstructor
public class GoogleAuthController {

    private static final String SESSION_POPUP_KEY = "GOOGLE_AUTH_POPUP";

    private final AuthService authService;
    private final GoogleOAuth2UserService googleOAuth2UserService;
    private final GoogleAuthSuccessHandler googleAuthSuccessHandler;
    private final AppProperties appProperties;

    @GetMapping
    public String initiateGoogleAuth(
            @RequestParam(value = "mode", defaultValue = "login") String mode,
            @RequestParam(value = "local", defaultValue = "false") boolean forceLocal,
            @RequestParam(value = "popup", defaultValue = "false") boolean popup,
            HttpServletRequest request,
            Model model
    ) {
        if (SecurityUtils.isAuthenticated()) {
            if (popup) {
                clearPopupFlag(request);
                return renderPopupComplete(true, "/", null, "Bạn đã đăng nhập.", model);
            }
            return "redirect:/";
        }

        // Chế độ 1: Khi đã cấu hình GOOGLE_CLIENT_ID & GOOGLE_CLIENT_SECRET thật -> Redirect sang Google OAuth2
        if (!forceLocal && googleOAuth2UserService.isRealOAuthConfigured()) {
            if (popup) {
                request.getSession(true).setAttribute(SESSION_POPUP_KEY, Boolean.TRUE);
            } else {
                clearPopupFlag(request);
            }
            String authUrl = googleOAuth2UserService.buildAuthorizationUrl(mode);
            log.info("Redirecting user to Google OAuth2 authorization screen (mode={}, popup={})", mode, popup);
            return "redirect:" + authUrl;
        }

        // Chế độ 2: Khi chạy Local/Demo chưa cấu hình Google Cloud OAuth2 -> Hiển thị Popup hoặc trang chọn tài khoản
        if (popup) {
            request.getSession(true).setAttribute(SESSION_POPUP_KEY, Boolean.TRUE);
            return renderGooglePopupPage(mode, model);
        }

        clearPopupFlag(request);
        return renderGoogleChooserPage(mode, model);
    }

    @GetMapping("/choose")
    public String googleAccountChooser(
            @RequestParam(value = "mode", defaultValue = "login") String mode,
            @RequestParam(value = "popup", defaultValue = "false") boolean popup,
            HttpServletRequest request,
            Model model
    ) {
        if (SecurityUtils.isAuthenticated()) {
            if (popup) {
                clearPopupFlag(request);
                return renderPopupComplete(true, "/", null, "Bạn đã đăng nhập.", model);
            }
            return "redirect:/";
        }
        if (popup) {
            request.getSession(true).setAttribute(SESSION_POPUP_KEY, Boolean.TRUE);
            return renderGooglePopupPage(mode, model);
        }
        clearPopupFlag(request);
        return renderGoogleChooserPage(mode, model);
    }

    @GetMapping("/callback")
    public String handleGoogleOAuthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "popup", defaultValue = "false") boolean popupParam,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        boolean popup = isPopupFlow(popupParam, request);

        if (error != null && !error.isBlank()) {
            log.warn("Google OAuth2 callback returned error: {}", error);
            return handleAuthError("Đăng nhập Google đã bị hủy hoặc từ chối quyền truy cập", popup, request, model);
        }

        if (code == null || code.isBlank()) {
            return handleAuthError("Không nhận được mã xác thực từ Google", popup, request, model);
        }

        try {
            GoogleOAuth2UserService.GoogleUserProfile profile = googleOAuth2UserService.exchangeCodeForProfile(code);
            return completeGoogleAuthentication(
                    profile.email(),
                    profile.fullName(),
                    profile.googleId(),
                    profile.avatarUrl(),
                    popup,
                    request,
                    response,
                    redirectAttributes,
                    model
            );
        } catch (BusinessException ex) {
            return handleAuthBusinessException(ex, popup, request, model);
        } catch (Exception ex) {
            log.error("Unexpected error in Google OAuth2 callback", ex);
            return handleAuthError("Xác thực tài khoản Google không thành công, vui lòng thử lại", popup, request, model);
        }
    }

    @PostMapping("/callback")
    public String handleGooglePostCallback(
            @RequestParam(value = "credential", required = false) String credential,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "googleId", required = false) String googleId,
            @RequestParam(value = "avatarUrl", required = false) String avatarUrl,
            @RequestParam(value = "popup", defaultValue = "false") boolean popupParam,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        boolean popup = isPopupFlow(popupParam, request);

        try {
            if (credential != null && !credential.isBlank()) {
                GoogleOAuth2UserService.GoogleUserProfile profile = googleOAuth2UserService.parseIdTokenPayload(credential);
                return completeGoogleAuthentication(
                        profile.email(),
                        profile.fullName(),
                        profile.googleId(),
                        profile.avatarUrl(),
                        popup,
                        request,
                        response,
                        redirectAttributes,
                        model
                );
            }

            return completeGoogleAuthentication(
                    email, fullName, googleId, avatarUrl, popup, request, response, redirectAttributes, model
            );
        } catch (BusinessException ex) {
            return handleAuthBusinessException(ex, popup, request, model);
        } catch (Exception ex) {
            log.error("Unexpected error in Google authentication submit", ex);
            return handleAuthError("Xác thực tài khoản Google không thành công, vui lòng thử lại", popup, request, model);
        }
    }

    @PostMapping("/confirm")
    public String confirmLocalGoogleAccount(
            @RequestParam("email") String email,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "googleId", required = false) String googleId,
            @RequestParam(value = "avatarUrl", required = false) String avatarUrl,
            @RequestParam(value = "popup", defaultValue = "false") boolean popupParam,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        boolean popup = isPopupFlow(popupParam, request);

        try {
            return completeGoogleAuthentication(
                    email, fullName, googleId, avatarUrl, popup, request, response, redirectAttributes, model
            );
        } catch (BusinessException ex) {
            return handleAuthBusinessException(ex, popup, request, model);
        } catch (Exception ex) {
            log.error("Unexpected error confirming Google account", ex);
            return handleAuthError("Xác thực tài khoản Google không thành công, vui lòng thử lại", popup, request, model);
        }
    }

    private String completeGoogleAuthentication(
            String email,
            String fullName,
            String googleId,
            String avatarUrl,
            boolean popup,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        AuthService.GoogleAuthResult authResult = authService.authenticateWithGoogleWithResult(
                email, fullName, googleId, avatarUrl
        );

        String targetUrl = googleAuthSuccessHandler.establishSessionAndDetermineTargetUrl(
                authResult.user(), request, response
        );

        String successMessage = authResult.newlyRegistered()
                ? "Đăng ký và đăng nhập bằng tài khoản Google thành công!"
                : "Đăng nhập bằng tài khoản Google thành công!";

        if (popup) {
            clearPopupFlag(request);
            return renderPopupComplete(true, targetUrl, null, successMessage, model);
        }

        redirectAttributes.addFlashAttribute("successMessage", successMessage);
        return "redirect:" + targetUrl;
    }

    private String renderGoogleChooserPage(String mode, Model model) {
        populateGoogleChooserModel(mode, model);
        return "auth/google-confirm";
    }

    private String renderGooglePopupPage(String mode, Model model) {
        populateGoogleChooserModel(mode, model);
        return "auth/google-popup";
    }

    private void populateGoogleChooserModel(String mode, Model model) {
        String normalizedMode = "register".equalsIgnoreCase(mode) ? "register" : "login";
        model.addAttribute("mode", normalizedMode);
        model.addAttribute("googleClientId",
                appProperties.getGoogle() != null ? appProperties.getGoogle().getClientId() : "");
    }

    private boolean isPopupFlow(boolean popupParam, HttpServletRequest request) {
        if (popupParam) {
            return true;
        }
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_POPUP_KEY));
    }

    private void clearPopupFlag(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_POPUP_KEY);
        }
    }

    private String handleAuthBusinessException(BusinessException ex, boolean popup, HttpServletRequest request, Model model) {
        String message = ex.getErrorCode() == ErrorCode.ACCOUNT_LOCKED
                ? "Tài khoản đã bị khóa, vui lòng liên hệ ban quản trị để được hỗ trợ"
                : ex.getMessage();
        return handleAuthError(message, popup, request, model);
    }

    private String handleAuthError(String errorMessage, boolean popup, HttpServletRequest request, Model model) {
        String errorUrl = buildLoginErrorUrl(errorMessage);
        if (popup) {
            clearPopupFlag(request);
            return renderPopupComplete(false, null, errorUrl, errorMessage, model);
        }
        return "redirect:" + errorUrl;
    }

    private String renderPopupComplete(boolean success, String redirectUrl, String errorUrl, String message, Model model) {
        model.addAttribute("success", success);
        model.addAttribute("redirectUrl", redirectUrl);
        model.addAttribute("errorUrl", errorUrl);
        model.addAttribute("message", message);
        return "auth/google-popup-complete";
    }

    private String buildLoginErrorUrl(String errorMessage) {
        String encoded = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        return "/login?error=" + encoded;
    }
}
