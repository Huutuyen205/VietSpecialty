package com.vietspecialty.service;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.dto.request.RegisterRequest;
import com.vietspecialty.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    /**
     * Kết quả xác thực / đăng ký qua Google OAuth2.
     */
    record GoogleAuthResult(User user, boolean newlyRegistered) {}

    /**
     * Xác thực hoặc tự động đăng ký tài khoản người dùng qua Google.
     *
     * @param email     Email từ tài khoản Google (bắt buộc)
     * @param fullName  Họ tên hiển thị từ Google
     * @param googleId  Sub/ID định danh từ Google (nếu có)
     * @param avatarUrl Ảnh đại diện từ Google (nếu có)
     * @return GoogleAuthResult chứa thông tin User và cờ đánh dấu tài khoản mới tạo
     */
    GoogleAuthResult authenticateWithGoogleWithResult(String email, String fullName, String googleId, String avatarUrl);

    /**
     * Xác thực hoặc tự động đăng ký tài khoản người dùng qua Google, trả về thực thể User.
     */
    default User authenticateWithGoogle(String email, String fullName, String googleId, String avatarUrl) {
        return authenticateWithGoogleWithResult(email, fullName, googleId, avatarUrl).user();
    }
}
