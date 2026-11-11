package com.vietspecialty.controller.api;

import com.vietspecialty.domain.entity.ChatMessage;
import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.request.ChatRequest;
import com.vietspecialty.dto.response.ChatResponse;
import com.vietspecialty.repository.ChatConversationRepository;
import com.vietspecialty.repository.ChatMessageRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @RequestBody ChatRequest request
    ) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        ChatResponse response = chatService.sendMessage(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHistory(
            @RequestParam String chatKey
    ) {
        if (chatKey == null || chatKey.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        var convOpt = conversationRepository.findByChatKey(chatKey);
        if (convOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        List<ChatMessage> messages = messageRepository.findRecentMessages(convOpt.get().getId(), PageRequest.of(0, 20));
        // Đảo ngược lại theo thứ tự thời gian tăng dần
        List<Map<String, Object>> result = messages.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(m -> Map.<String, Object>of(
                        "role", m.getRole().name().toLowerCase(),
                        "content", m.getContent(),
                        "time", m.getCreatedAt().toString()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

