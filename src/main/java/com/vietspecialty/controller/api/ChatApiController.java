package com.vietspecialty.controller.api;

import com.vietspecialty.domain.entity.ChatMessage;
import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.request.ChatRequest;
import com.vietspecialty.dto.response.ChatResponse;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.mapper.ProductMapper;
import com.vietspecialty.repository.ChatConversationRepository;
import com.vietspecialty.repository.ChatMessageRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/chat", "/api/chatbot"})
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ProductRepository productRepository;

    @PostMapping({"", "/send", "/message"})
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @RequestBody ChatRequest request
    ) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        ChatResponse response = chatService.sendMessage(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHistory(
            @RequestParam String chatKey
    ) {
        if (chatKey == null || chatKey.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        var convOpt = conversationRepository.findByChatKey(chatKey.trim());
        if (convOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        List<ChatMessage> messages = messageRepository.findRecentMessages(convOpt.get().getId(), PageRequest.of(0, 20));

        // Tập hợp toàn bộ productId từ lịch sử để truy vấn 1 lần
        Set<Long> allProductIds = new LinkedHashSet<>();
        for (ChatMessage m : messages) {
            if (m.getSuggestedProductIds() != null && !m.getSuggestedProductIds().isBlank()) {
                for (String raw : m.getSuggestedProductIds().split(",")) {
                    try {
                        allProductIds.add(Long.parseLong(raw.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        Map<Long, ProductCardResponse> productCardMap = new HashMap<>();
        if (!allProductIds.isEmpty()) {
            List<Product> products = productRepository.findAllById(allProductIds);
            for (Product p : products) {
                productCardMap.put(p.getId(), ProductMapper.toCardResponse(p));
            }
        }

        // Đảo ngược lại theo thứ tự thời gian & ID tăng dần
        List<Map<String, Object>> result = messages.stream()
                .sorted(Comparator.comparing(ChatMessage::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(m -> {
                    List<ProductCardResponse> suggestedProducts = new ArrayList<>();
                    if (m.getSuggestedProductIds() != null && !m.getSuggestedProductIds().isBlank()) {
                        for (String raw : m.getSuggestedProductIds().split(",")) {
                            try {
                                Long pid = Long.parseLong(raw.trim());
                                ProductCardResponse card = productCardMap.get(pid);
                                if (card != null) {
                                    suggestedProducts.add(card);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("role", m.getRole().name().toLowerCase());
                    item.put("content", m.getContent());
                    item.put("intent", m.getIntent() != null ? m.getIntent().name() : "OTHER");
                    item.put("suggestedProducts", suggestedProducts);
                    item.put("time", m.getCreatedAt().toString());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetConversation(
            @RequestParam(required = false) String chatKey,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String newChatKey = "chat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chatKey", newChatKey);
        data.put("message", "Đã làm mới cuộc trò chuyện cùng Trợ lý AI VietSpecialty");
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
