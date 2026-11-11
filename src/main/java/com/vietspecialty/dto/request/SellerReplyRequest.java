package com.vietspecialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerReplyRequest(
        @NotBlank(message = "Nội dung phản hồi không được để trống")
        @Size(min = 2, max = 1000, message = "Nội dung phản hồi từ 2 đến 1000 ký tự")
        String reply
) {}

