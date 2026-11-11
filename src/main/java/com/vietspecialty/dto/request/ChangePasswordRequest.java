package com.vietspecialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String oldPassword,

        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Size(min = 8, max = 64, message = "Mật khẩu mới phải từ 8 đến 64 ký tự")
        String newPassword,

        @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
        String confirmNewPassword
) {
    public boolean isPasswordMatching() {
        return newPassword != null && newPassword.equals(confirmNewPassword);
    }
}

