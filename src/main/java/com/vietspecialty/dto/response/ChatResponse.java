package com.vietspecialty.dto.response;

import com.vietspecialty.domain.enums.ChatIntent;

import java.util.List;

public record ChatResponse(
        String reply,
        ChatIntent intent,
        String chatKey,
        List<ProductCardResponse> suggestedProducts,
        List<String> suggestedQuestions
) {}

