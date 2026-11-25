-- =====================================================================
-- V8: Bổ sung cột password_set cho bảng users (DEF-15)
-- Phân biệt tài khoản đăng ký/đăng nhập bằng mật khẩu (1)
-- và tài khoản tự động tạo qua Google OAuth2 chưa thiết lập mật khẩu (0).
-- =====================================================================
ALTER TABLE users
    ADD COLUMN password_set TINYINT(1) NOT NULL DEFAULT 1 AFTER password_hash;

UPDATE users
SET password_set = 0
WHERE email = 'nguyenvanan.google@gmail.com'
   OR (email LIKE 'google|_%' ESCAPE '|' AND phone IS NULL);
