package com.vietspecialty.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private static final List<String> FALLBACK_MODELS = List.of(
            "gemini-2.0-flash",
            "gemini-1.5-flash"
    );

    /**
     * Gửi yêu cầu sinh nội dung đơn lượt (tương thích ngược)
     */
    public String generateContent(String systemInstruction, String userPrompt) {
        return generateMultiTurnContent(systemInstruction, List.of(), userPrompt);
    }

    /**
     * Gửi yêu cầu hội thoại đa lượt (Multi-turn Conversational RAG) tới Gemini REST API.
     * Tự động kiểm tra định dạng Google AI Studio API Key (bắt đầu bằng "AIza") và hỗ trợ fallback model.
     */
    public String generateMultiTurnContent(
            String systemInstruction,
            List<Map<String, String>> historyTurns,
            String currentMessage
    ) {
        if (appProperties.getAi() == null || appProperties.getAi().getGemini() == null) {
            return null;
        }

        String rawKey = appProperties.getAi().getGemini().getApiKey();
        if (!isValidGoogleAiStudioKey(rawKey)) {
            log.debug("Gemini API key is missing or not a valid Google AI Studio key (must start with 'AIza'). Using local grounded reasoning engine.");
            return null;
        }
        String apiKey = rawKey.trim();

        String configuredModel = appProperties.getAi().getGemini().getModel();
        List<String> candidateModels = buildCandidateModels(configuredModel);

        Map<String, Object> requestBody = buildRequestBody(systemInstruction, historyTurns, currentMessage);

        int timeoutSeconds = Math.max(4, Math.min(15, appProperties.getAi().getGemini().getTimeoutSeconds()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutSeconds * 1000);
        requestFactory.setReadTimeout(timeoutSeconds * 1000);

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        for (String model : candidateModels) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent?key=" + apiKey;

                String responseJson = restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                if (responseJson != null && !responseJson.isBlank()) {
                    JsonNode root = objectMapper.readTree(responseJson);
                    JsonNode textNode = root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text");
                    if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                        return textNode.asText().trim();
                    }
                }
            } catch (Exception e) {
                log.warn("Gemini API call failed on model {}: {}. Trying fallback if available.", model, e.getMessage());
            }
        }

        return null;
    }

    public boolean isValidGoogleAiStudioKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String trimmed = apiKey.trim();
        return trimmed.startsWith("AIza") && trimmed.length() >= 30;
    }

    private List<String> buildCandidateModels(String configuredModel) {
        List<String> models = new ArrayList<>();
        if (configuredModel != null && !configuredModel.isBlank()) {
            models.add(configuredModel.trim());
        }
        for (String fb : FALLBACK_MODELS) {
            if (!models.contains(fb)) {
                models.add(fb);
            }
        }
        return models;
    }

    private Map<String, Object> buildRequestBody(
            String systemInstruction,
            List<Map<String, String>> historyTurns,
            String currentMessage
    ) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.put("system_instruction", Map.of(
                    "parts", List.of(Map.of("text", systemInstruction.trim()))
            ));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        String lastRole = null;

        if (historyTurns != null) {
            for (Map<String, String> turn : historyTurns) {
                if (turn == null) continue;
                String rawRole = turn.getOrDefault("role", "user");
                String role = ("bot".equalsIgnoreCase(rawRole) || "model".equalsIgnoreCase(rawRole))
                        ? "model"
                        : "user";
                String text = turn.getOrDefault("content", turn.getOrDefault("text", ""));
                if (text == null || text.isBlank()) continue;

                // Gemini API yêu cầu lượt đầu tiên trong contents nên là "user" và không lặp role liên tiếp
                if (contents.isEmpty() && "model".equals(role)) {
                    continue;
                }
                if (role.equals(lastRole) && !contents.isEmpty()) {
                    // Gộp vào turn trước nếu cùng role
                    Map<String, Object> prev = contents.get(contents.size() - 1);
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> prevParts = (List<Map<String, String>>) prev.get("parts");
                    String merged = prevParts.get(0).get("text") + "\n" + text.trim();
                    prev.put("parts", List.of(Map.of("text", merged)));
                } else {
                    Map<String, Object> turnMap = new LinkedHashMap<>();
                    turnMap.put("role", role);
                    turnMap.put("parts", List.of(Map.of("text", text.trim())));
                    contents.add(turnMap);
                    lastRole = role;
                }
            }
        }

        String userText = currentMessage != null ? currentMessage.trim() : "";
        if ("user".equals(lastRole) && !contents.isEmpty()) {
            Map<String, Object> prev = contents.get(contents.size() - 1);
            @SuppressWarnings("unchecked")
            List<Map<String, String>> prevParts = (List<Map<String, String>>) prev.get("parts");
            String merged = prevParts.get(0).get("text") + "\n" + userText;
            prev.put("parts", List.of(Map.of("text", merged)));
        } else {
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", userText))
            ));
        }

        body.put("contents", contents);
        body.put("generationConfig", Map.of(
                "temperature", 0.35,
                "maxOutputTokens", 900,
                "topP", 0.9
        ));

        return body;
    }
}
