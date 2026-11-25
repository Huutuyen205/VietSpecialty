package com.vietspecialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
        String fullName,

        @Pattern(regexp = "^$|^(0|\\+84)[35789][0-9]{8}$", message = "Số điện thoại không đúng định dạng Việt Nam")
        String phone
) {
    public ProfileUpdateRequest {
        fullName = fullName != null ? fullName.trim() : null;
        phone = (phone == null || phone.isBlank()) ? "" : phone.trim();
    }
}

