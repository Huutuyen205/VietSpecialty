package com.vietspecialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 500, message = "Tin nhắn tối đa 500 ký tự")
        String message,

        String chatKey
) {}

