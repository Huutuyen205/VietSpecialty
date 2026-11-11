package com.vietspecialty.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    /**
     * Gửi yêu cầu sinh nội dung tới Gemini 1.5 Flash
     */
    public String generateContent(String systemInstruction, String userPrompt) {
        String apiKey = appProperties.getAi().getGemini().getApiKey();
        String model = appProperties.getAi().getGemini().getModel();

        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Gemini API key is not configured. Falling back to offline response generator.");
            return null;
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            String fullPrompt = (systemInstruction != null && !systemInstruction.isBlank())
                    ? systemInstruction + "\n\n" + userPrompt
                    : userPrompt;

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", fullPrompt))
                            )
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.4,
                            "maxOutputTokens", 800
                    )
            );

            int timeout = Math.max(5, appProperties.getAi().getGemini().getTimeoutSeconds());
            RestClient restClient = RestClient.builder()
                    .build();

            String responseJson = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseJson != null) {
                JsonNode root = objectMapper.readTree(responseJson);
                JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                if (!textNode.isMissingNode()) {
                    return textNode.asText().trim();
                }
            }
        } catch (Exception e) {
            log.warn("Gemini API call failed: {}. Falling back to offline generator.", e.getMessage());
        }

        return null;
    }
}

