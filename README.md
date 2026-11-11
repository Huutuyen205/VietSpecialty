# VietSpecialty — Sàn Thương Mại Điện Tử Đặc Sản Vùng Miền Tích Hợp Chatbot AI

> **Đồ án tốt nghiệp Đại học ngành Công nghệ Thông tin**  
> **Sinh viên:** Nguyễn Hữu Tuyên — MSSV: 2310900105 — Lớp: K23CNT1 — Trường Đại học Nguyễn Trãi  
> **Giảng viên hướng dẫn:** ThS. Nguyễn Thị Duyên  
> **Công nghệ:** Java 21 · Spring Boot 3.4.x · MySQL 8 · Flyway · Thymeleaf · Tailwind CSS · Alpine.js · Google Gemini API · VNPay Sandbox

---

## 1. Yêu cầu môi trường
- **Java:** JDK 21 (hoặc JDK 17+)
- **Maven:** 3.9+ (hoặc dùng Maven Wrapper)
- **Node.js:** v18+ (chỉ cần khi muốn chỉnh sửa và build lại Tailwind CSS)
- **MySQL:** 8.0+ đang chạy ở cổng `3306`

---

## 2. Hướng dẫn khởi chạy ứng dụng

### Bước 1: Chuẩn bị CSDL MySQL
Tạo cơ sở dữ liệu `vietspecialty` với mã hóa UTF-8:
```sql
CREATE DATABASE vietspecialty CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Bước 2: Thiết lập file môi trường
Sao chép file cấu hình mẫu `.env.example` thành `.env`:
```bash
cp .env.example .env
```
Cập nhật thông tin kết nối MySQL và các khóa bí mật trong `.env`:
- `DB_URL`: Chuỗi kết nối JDBC
- `DB_USERNAME`: Tài khoản MySQL (ví dụ: `root`)
- `DB_PASSWORD`: Mật khẩu MySQL
- `GEMINI_API_KEY`: Khóa API Google Gemini AI
- `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`: Khóa cổng thanh toán VNPay Sandbox

### Bước 3: Build CSS (Tùy chọn nếu có sửa đổi giao diện)
```bash
npm install
npm run build:css
```
*(File CSS `src/main/resources/static/css/app.css` đã được biên dịch sẵn trong mã nguồn).*

### Bước 4: Khởi động ứng dụng Spring Boot
```bash
mvn spring-boot:run
```
Khi ứng dụng khởi động lần đầu, Flyway sẽ tự động chạy 2 bản migration:
- `V1__schema.sql`: Khởi tạo cấu trúc 21 bảng.
- `V2__master_data.sql`: Nạp dữ liệu 4 miền, 34 tỉnh/thành phố và 8 danh mục đặc sản.

Truy cập ứng dụng tại: **[http://localhost:8080](http://localhost:8080)**

---

## 3. Cấu trúc tài khoản mặc định (Demo Profile)
- **Quản trị viên (Admin):** `admin@vietspecialty.vn` / `Admin@123456`
- **Người mua (Buyer 1):** `buyer1@demo.vn` / `Demo@1234`
- **Người mua (Buyer 2):** `buyer2@demo.vn` / `Demo@1234`
- **Người bán Miền Bắc:** `seller.north@demo.vn` / `Demo@1234`
- **Người bán Miền Trung:** `seller.central@demo.vn` / `Demo@1234`
- **Người bán Miền Nam:** `seller.south@demo.vn` / `Demo@1234`

