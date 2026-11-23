package com.vietspecialty.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.config.AppProperties;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Dịch vụ xử lý giao thức Google OAuth2 (Authorization Code Flow & Google Identity Services ID Token).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuth2UserService {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public record GoogleUserProfile(
            String email,
            String fullName,
            String googleId,
            String avatarUrl
    ) {}

    /**
     * Kiểm tra hệ thống đã được cấu hình GOOGLE_CLIENT_ID & GOOGLE_CLIENT_SECRET thật hay chưa.
     */
    public boolean isRealOAuthConfigured() {
        AppProperties.Google google = appProperties.getGoogle();
        return google != null
                && google.getClientId() != null && !google.getClientId().isBlank()
                && google.getClientSecret() != null && !google.getClientSecret().isBlank();
    }

    public boolean hasClientId() {
        AppProperties.Google google = appProperties.getGoogle();
        return google != null && google.getClientId() != null && !google.getClientId().isBlank();
    }

    /**
     * Xây dựng URL chuyển hướng sang màn hình đăng nhập Google OAuth2 thật.
     */
    public String buildAuthorizationUrl(String state) {
        AppProperties.Google google = appProperties.getGoogle();
        String redirectUri = (google.getRedirectUri() != null && !google.getRedirectUri().isBlank())
                ? google.getRedirectUri()
                : appProperties.getBaseUrl() + "/auth/google/callback";

        return UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", google.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .queryParam("state", state != null ? state : "login")
                .build()
                .encode()
                .toUriString();
    }

    /**
     * Đổi authorization code từ Google lấy thông tin hồ sơ người dùng.
     */
    public GoogleUserProfile exchangeCodeForProfile(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã xác thực Google OAuth2 không hợp lệ");
        }

        AppProperties.Google google = appProperties.getGoogle();
        String redirectUri = (google.getRedirectUri() != null && !google.getRedirectUri().isBlank())
                ? google.getRedirectUri()
                : appProperties.getBaseUrl() + "/auth/google/callback";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", code);
            body.add("client_id", google.getClientId());
            body.add("client_secret", google.getClientSecret());
            body.add("redirect_uri", redirectUri);
            body.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(body, headers);
            ResponseEntity<String> tokenResponse = restTemplate.postForEntity(GOOGLE_TOKEN_URL, tokenRequest, String.class);

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
            String idToken = tokenJson.path("id_token").asText(null);
            String accessToken = tokenJson.path("access_token").asText(null);

            if (idToken != null && !idToken.isBlank()) {
                return parseIdTokenPayload(idToken);
            }

            if (accessToken != null && !accessToken.isBlank()) {
                HttpHeaders userHeaders = new HttpHeaders();
                userHeaders.setBearerAuth(accessToken);
                HttpEntity<Void> userRequest = new HttpEntity<>(userHeaders);
                ResponseEntity<String> userResponse = restTemplate.exchange(
                        GOOGLE_USERINFO_URL, HttpMethod.GET, userRequest, String.class);
                JsonNode userJson = objectMapper.readTree(userResponse.getBody());
                return extractProfileFromJson(userJson);
            }

            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Không nhận được token hợp lệ từ Google");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to exchange Google OAuth2 code", ex);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Không thể xác thực tài khoản với máy chủ Google");
        }
    }

    /**
     * Giải mã Google Identity Services (GIS) JWT ID Token credential.
     */
    public GoogleUserProfile parseIdTokenPayload(String credentialJwt) {
        if (credentialJwt == null || credentialJwt.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Google ID Token không được để trống");
        }
        try {
            String[] parts = credentialJwt.split("\\.");
            String payloadJsonStr;
            if (parts.length >= 2) {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
                payloadJsonStr = new String(decodedBytes, StandardCharsets.UTF_8);
            } else {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(credentialJwt);
                payloadJsonStr = new String(decodedBytes, StandardCharsets.UTF_8);
            }
            JsonNode payload = objectMapper.readTree(payloadJsonStr);
            return extractProfileFromJson(payload);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Invalid Google ID token format", ex);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Google ID Token không hợp lệ");
        }
    }

    private GoogleUserProfile extractProfileFromJson(JsonNode node) {
        String email = node.path("email").asText("").trim().toLowerCase();
        if (email.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tài khoản Google không cung cấp địa chỉ email");
        }
        String name = node.path("name").asText("").trim();
        String sub = node.path("sub").asText("google_" + email.hashCode());
        String picture = node.path("picture").asText(null);
        return new GoogleUserProfile(email, name, sub, picture);
    }
}
