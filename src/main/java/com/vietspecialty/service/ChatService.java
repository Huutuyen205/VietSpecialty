package com.vietspecialty.service;

import com.vietspecialty.dto.request.ChatRequest;
import com.vietspecialty.dto.response.ChatResponse;

public interface ChatService {

    ChatResponse sendMessage(Long userId, ChatRequest request);
}

