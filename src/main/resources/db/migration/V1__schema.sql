-- =====================================================================
-- VietSpecialty - Database Schema v2 (MySQL 8.0+)
-- Sàn TMĐT đặc sản vùng miền tích hợp Chatbot AI
-- 21 bảng. Tiền tệ VND lưu DECIMAL(15,0).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------
CREATE TABLE users (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  full_name     VARCHAR(100)  NOT NULL,
  email         VARCHAR(150)  NOT NULL,
  password_hash VARCHAR(255)  NOT NULL,
  phone         VARCHAR(15)   NULL,
  avatar_url    VARCHAR(500)  NULL,
  role          ENUM('USER','SELLER','ADMIN') NOT NULL DEFAULT 'USER',
  status        ENUM('ACTIVE','LOCKED')       NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 2. regions (Miền Bắc, Miền Trung, Tây Nguyên, Miền Nam)
-- ---------------------------------------------------------------------
CREATE TABLE regions (
  id          INT           NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100)  NOT NULL,
  slug        VARCHAR(100)  NOT NULL,
  description VARCHAR(500)  NULL,
  color_hex   VARCHAR(7)      NULL,
  sort_order  INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_regions_slug (slug)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 3. provinces (34 tỉnh/thành sau sắp xếp 01/07/2025)
-- ---------------------------------------------------------------------
CREATE TABLE provinces (
  id         INT           NOT NULL AUTO_INCREMENT,
  region_id  INT           NOT NULL,
  name       VARCHAR(100)  NOT NULL,
  slug       VARCHAR(100)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_provinces_slug (slug),
  KEY idx_provinces_region (region_id),
  CONSTRAINT fk_provinces_region FOREIGN KEY (region_id) REFERENCES regions(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 4. categories
-- ---------------------------------------------------------------------
CREATE TABLE categories (
  id          INT           NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100)  NOT NULL,
  slug        VARCHAR(100)  NOT NULL,
  description VARCHAR(500)  NULL,
  icon        VARCHAR(100)  NULL,
  sort_order  INT           NOT NULL DEFAULT 0,
  is_active   TINYINT(1)    NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_slug (slug)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 5. addresses (địa chỉ nhận hàng: tỉnh/thành -> phường/xã -> chi tiết)
-- ---------------------------------------------------------------------
CREATE TABLE addresses (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  user_id        BIGINT        NOT NULL,
  receiver_name  VARCHAR(100)  NOT NULL,
  receiver_phone VARCHAR(15)   NOT NULL,
  province_id    INT           NOT NULL,
  ward           VARCHAR(150)  NOT NULL,
  street_address VARCHAR(255)  NOT NULL,
  is_default     TINYINT(1)    NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_addresses_user (user_id),
  CONSTRAINT fk_addresses_user     FOREIGN KEY (user_id)     REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_addresses_province FOREIGN KEY (province_id) REFERENCES provinces(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 6. shops
-- ---------------------------------------------------------------------
CREATE TABLE shops (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  owner_id       BIGINT        NOT NULL,
  name           VARCHAR(150)  NOT NULL,
  slug           VARCHAR(170)  NOT NULL,
  description    TEXT          NULL,
  logo_url       VARCHAR(500)  NULL,
  logo_public_id VARCHAR(255)  NULL,
  phone          VARCHAR(15)   NULL,
  province_id    INT           NOT NULL,
  address_detail VARCHAR(255)  NULL,
  status         ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
  reject_reason  VARCHAR(500)  NULL,
  approved_at    DATETIME      NULL,
  approved_by    BIGINT        NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shops_owner (owner_id),
  UNIQUE KEY uk_shops_name (name),
  UNIQUE KEY uk_shops_slug (slug),
  KEY idx_shops_status (status),
  CONSTRAINT fk_shops_owner    FOREIGN KEY (owner_id)    REFERENCES users(id),
  CONSTRAINT fk_shops_province FOREIGN KEY (province_id) REFERENCES provinces(id),
  CONSTRAINT fk_shops_approver FOREIGN KEY (approved_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 7. products (vùng miền suy ra qua province.region_id)
-- ---------------------------------------------------------------------
CREATE TABLE products (
  id                   BIGINT        NOT NULL AUTO_INCREMENT,
  shop_id              BIGINT        NOT NULL,
  category_id          INT           NOT NULL,
  province_id          INT           NOT NULL,           -- tỉnh xuất xứ
  name                 VARCHAR(255)  NOT NULL,
  slug                 VARCHAR(280)  NOT NULL,
  short_description    VARCHAR(500)  NULL,
  description          TEXT          NULL,
  price                DECIMAL(15,0) NOT NULL,
  stock                INT           NOT NULL DEFAULT 0,
  unit                 VARCHAR(50)   NOT NULL,           -- "Hộp 500g", "Chai 500ml"
  weight_gram          INT           NULL,
  origin_detail        VARCHAR(255)  NULL,               -- "Phú Quốc", "Thái Nguyên"...
  shelf_life           VARCHAR(100)  NULL,               -- hạn sử dụng
  storage_instructions VARCHAR(500)  NULL,               -- cách bảo quản
  ocop_rating          TINYINT       NULL,               -- 3..5 sao, NULL nếu không có
  sold_count           INT           NOT NULL DEFAULT 0,
  avg_rating           DECIMAL(2,1)  NOT NULL DEFAULT 0.0,
  review_count         INT           NOT NULL DEFAULT 0,
  status               ENUM('ACTIVE','HIDDEN','DELETED') NOT NULL DEFAULT 'ACTIVE',
  search_text          VARCHAR(1000) NULL,               -- tên + xuất xứ không dấu, chữ thường
  version              INT           NOT NULL DEFAULT 0, -- optimistic lock
  created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_products_slug (slug),
  KEY idx_products_shop (shop_id),
  KEY idx_products_category (category_id),
  KEY idx_products_province (province_id),
  KEY idx_products_status_price (status, price),
  KEY idx_products_sold (sold_count),
  CONSTRAINT chk_products_price CHECK (price > 0),
  CONSTRAINT chk_products_stock CHECK (stock >= 0),
  CONSTRAINT chk_products_ocop  CHECK (ocop_rating IS NULL OR ocop_rating BETWEEN 1 AND 5),
  CONSTRAINT fk_products_shop     FOREIGN KEY (shop_id)     REFERENCES shops(id),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT fk_products_province FOREIGN KEY (province_id) REFERENCES provinces(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 8. product_images (Cloudinary)
-- ---------------------------------------------------------------------
CREATE TABLE product_images (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  product_id BIGINT        NOT NULL,
  image_url  VARCHAR(500)  NOT NULL,
  public_id  VARCHAR(255)  NULL,
  sort_order INT           NOT NULL DEFAULT 0,
  is_primary TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_product_images_product (product_id),
  CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 9. cart_items
-- ---------------------------------------------------------------------
CREATE TABLE cart_items (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   NOT NULL,
  product_id BIGINT   NOT NULL,
  quantity   INT      NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cart_user_product (user_id, product_id),
  CONSTRAINT chk_cart_quantity CHECK (quantity > 0),
  CONSTRAINT fk_cart_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
  CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 10. vouchers (SHOP: mã của gian hàng | PLATFORM: mã toàn sàn)
-- ---------------------------------------------------------------------
CREATE TABLE vouchers (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  code            VARCHAR(50)   NOT NULL,
  name            VARCHAR(150)  NOT NULL,
  scope           ENUM('SHOP','PLATFORM') NOT NULL,
  shop_id         BIGINT        NULL,
  discount_type   ENUM('PERCENT','FIXED') NOT NULL,
  discount_value  DECIMAL(15,0) NOT NULL,                -- % (1..100) hoặc số tiền
  max_discount    DECIMAL(15,0) NULL,                    -- trần cho PERCENT
  min_order_value DECIMAL(15,0) NOT NULL DEFAULT 0,
  usage_limit     INT           NULL,                    -- NULL = không giới hạn
  used_count      INT           NOT NULL DEFAULT 0,
  per_user_limit  INT           NOT NULL DEFAULT 1,
  start_at        DATETIME      NOT NULL,
  end_at          DATETIME      NOT NULL,
  is_active       TINYINT(1)    NOT NULL DEFAULT 1,
  created_by      BIGINT        NOT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_vouchers_code (code),
  KEY idx_vouchers_shop (shop_id),
  CONSTRAINT chk_vouchers_scope CHECK ((scope = 'SHOP' AND shop_id IS NOT NULL) OR (scope = 'PLATFORM' AND shop_id IS NULL)),
  CONSTRAINT chk_vouchers_value CHECK (discount_value > 0 AND (discount_type <> 'PERCENT' OR discount_value <= 100)),
  CONSTRAINT chk_vouchers_dates CHECK (end_at > start_at),
  CONSTRAINT fk_vouchers_shop    FOREIGN KEY (shop_id)    REFERENCES shops(id),
  CONSTRAINT fk_vouchers_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 11. order_groups (1 lần checkout = 1 nhóm, tách thành nhiều orders theo gian hàng;
--     VNPay thanh toán 1 lần cho cả nhóm)
-- ---------------------------------------------------------------------
CREATE TABLE order_groups (
  id                      BIGINT        NOT NULL AUTO_INCREMENT,
  code                    VARCHAR(30)   NOT NULL,
  user_id                 BIGINT        NOT NULL,
  payment_method          ENUM('COD','VNPAY') NOT NULL,
  platform_voucher_id     BIGINT        NULL,
  items_subtotal          DECIMAL(15,0) NOT NULL,
  shipping_total          DECIMAL(15,0) NOT NULL DEFAULT 0,
  shop_discount_total     DECIMAL(15,0) NOT NULL DEFAULT 0,
  platform_discount_total DECIMAL(15,0) NOT NULL DEFAULT 0,
  grand_total             DECIMAL(15,0) NOT NULL,
  payment_status          ENUM('UNPAID','PAID','FAILED','EXPIRED','NOT_REQUIRED') NOT NULL,
  payment_expires_at      DATETIME      NULL,           -- VNPay: created_at + 15 phút
  created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_groups_code (code),
  KEY idx_order_groups_user (user_id),
  KEY idx_order_groups_pay (payment_status, payment_expires_at),
  CONSTRAINT fk_order_groups_user    FOREIGN KEY (user_id)             REFERENCES users(id),
  CONSTRAINT fk_order_groups_voucher FOREIGN KEY (platform_voucher_id) REFERENCES vouchers(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 12. orders (mỗi gian hàng 1 đơn; địa chỉ được SNAPSHOT, không FK addresses)
-- ---------------------------------------------------------------------
CREATE TABLE orders (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  code              VARCHAR(30)   NOT NULL,
  order_group_id    BIGINT        NOT NULL,
  user_id           BIGINT        NOT NULL,
  shop_id           BIGINT        NOT NULL,
  receiver_name     VARCHAR(100)  NOT NULL,
  receiver_phone    VARCHAR(15)   NOT NULL,
  shipping_address  VARCHAR(500)  NOT NULL,
  shipping_method   ENUM('STANDARD','EXPRESS') NOT NULL DEFAULT 'STANDARD',
  items_subtotal    DECIMAL(15,0) NOT NULL,
  shipping_fee      DECIMAL(15,0) NOT NULL DEFAULT 0,
  shop_voucher_id   BIGINT        NULL,
  shop_discount     DECIMAL(15,0) NOT NULL DEFAULT 0,
  platform_discount DECIMAL(15,0) NOT NULL DEFAULT 0,
  total_amount      DECIMAL(15,0) NOT NULL,
  payment_method    ENUM('COD','VNPAY') NOT NULL,
  payment_status    ENUM('UNPAID','PAID','REFUND_PENDING','REFUNDED') NOT NULL DEFAULT 'UNPAID',
  status            ENUM('PENDING_PAYMENT','PENDING_CONFIRM','PREPARING','SHIPPING','DELIVERED','CANCELLED') NOT NULL,
  cancel_reason     VARCHAR(500)  NULL,
  cancelled_by      ENUM('BUYER','SELLER','ADMIN','SYSTEM') NULL,
  carrier_name      VARCHAR(100)  NULL,
  tracking_code     VARCHAR(100)  NULL,
  note              VARCHAR(500)  NULL,
  confirmed_at      DATETIME      NULL,
  shipped_at        DATETIME      NULL,
  delivered_at      DATETIME      NULL,
  cancelled_at      DATETIME      NULL,
  version           INT           NOT NULL DEFAULT 0,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_code (code),
  KEY idx_orders_group (order_group_id),
  KEY idx_orders_user_created (user_id, created_at),
  KEY idx_orders_shop_status (shop_id, status),
  KEY idx_orders_status_delivered (status, delivered_at),
  CONSTRAINT fk_orders_group   FOREIGN KEY (order_group_id)  REFERENCES order_groups(id),
  CONSTRAINT fk_orders_user    FOREIGN KEY (user_id)         REFERENCES users(id),
  CONSTRAINT fk_orders_shop    FOREIGN KEY (shop_id)         REFERENCES shops(id),
  CONSTRAINT fk_orders_voucher FOREIGN KEY (shop_voucher_id) REFERENCES vouchers(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 13. order_items (snapshot tên, ảnh, đơn vị, giá lúc đặt)
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  order_id          BIGINT        NOT NULL,
  product_id        BIGINT        NOT NULL,
  product_name      VARCHAR(255)  NOT NULL,
  product_image_url VARCHAR(500)  NULL,
  unit              VARCHAR(50)   NOT NULL,
  unit_price        DECIMAL(15,0) NOT NULL,
  quantity          INT           NOT NULL,
  line_total        DECIMAL(15,0) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_items_order (order_id),
  KEY idx_order_items_product (product_id),
  CONSTRAINT chk_order_items_qty CHECK (quantity > 0),
  CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 14. order_status_history
-- ---------------------------------------------------------------------
CREATE TABLE order_status_history (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  order_id    BIGINT       NOT NULL,
  from_status ENUM('PENDING_PAYMENT','PENDING_CONFIRM','PREPARING','SHIPPING','DELIVERED','CANCELLED') NULL,
  to_status   ENUM('PENDING_PAYMENT','PENDING_CONFIRM','PREPARING','SHIPPING','DELIVERED','CANCELLED') NOT NULL,
  actor_type  ENUM('BUYER','SELLER','ADMIN','SYSTEM') NOT NULL,
  changed_by  BIGINT       NULL,
  note        VARCHAR(500) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_osh_order (order_id),
  CONSTRAINT fk_osh_order FOREIGN KEY (order_id)   REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_osh_user  FOREIGN KEY (changed_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 15. voucher_usages (đếm lượt dùng theo người dùng; RELEASED khi đơn bị hủy)
-- ---------------------------------------------------------------------
CREATE TABLE voucher_usages (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  voucher_id      BIGINT        NOT NULL,
  user_id         BIGINT        NOT NULL,
  order_group_id  BIGINT        NOT NULL,
  order_id        BIGINT        NULL,                    -- NULL với mã PLATFORM
  discount_amount DECIMAL(15,0) NOT NULL,
  status          ENUM('APPLIED','RELEASED') NOT NULL DEFAULT 'APPLIED',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_vu_voucher_user (voucher_id, user_id, status),
  CONSTRAINT fk_vu_voucher FOREIGN KEY (voucher_id)     REFERENCES vouchers(id),
  CONSTRAINT fk_vu_user    FOREIGN KEY (user_id)        REFERENCES users(id),
  CONSTRAINT fk_vu_group   FOREIGN KEY (order_group_id) REFERENCES order_groups(id),
  CONSTRAINT fk_vu_order   FOREIGN KEY (order_id)       REFERENCES orders(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 16. payments (mỗi lần gọi VNPay = 1 bản ghi; thanh toán lại = txn_ref mới)
-- ---------------------------------------------------------------------
CREATE TABLE payments (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  order_group_id     BIGINT        NOT NULL,
  method             ENUM('VNPAY') NOT NULL DEFAULT 'VNPAY',
  txn_ref            VARCHAR(50)   NOT NULL,
  amount             DECIMAL(15,0) NOT NULL,
  status             ENUM('PENDING','SUCCESS','FAILED','EXPIRED') NOT NULL DEFAULT 'PENDING',
  vnp_transaction_no VARCHAR(50)   NULL,
  vnp_response_code  VARCHAR(10)   NULL,
  vnp_bank_code      VARCHAR(20)   NULL,
  vnp_pay_date       VARCHAR(14)   NULL,
  raw_callback       TEXT          NULL,
  paid_at            DATETIME      NULL,
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_txn_ref (txn_ref),
  KEY idx_payments_group (order_group_id),
  CONSTRAINT fk_payments_group FOREIGN KEY (order_group_id) REFERENCES order_groups(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 17. reviews (1 order_item = tối đa 1 đánh giá)
-- ---------------------------------------------------------------------
CREATE TABLE reviews (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  order_item_id     BIGINT        NOT NULL,
  product_id        BIGINT        NOT NULL,
  user_id           BIGINT        NOT NULL,
  rating            TINYINT       NOT NULL,
  content           VARCHAR(1000) NULL,
  status            ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
  seller_reply      VARCHAR(1000) NULL,
  seller_replied_at DATETIME      NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reviews_order_item (order_item_id),
  KEY idx_reviews_product_status (product_id, status),
  CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
  CONSTRAINT fk_reviews_product    FOREIGN KEY (product_id)    REFERENCES products(id),
  CONSTRAINT fk_reviews_user       FOREIGN KEY (user_id)       REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 18. product_comments (bình luận / hỏi đáp dưới sản phẩm, có trả lời 1 cấp)
-- ---------------------------------------------------------------------
CREATE TABLE product_comments (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  product_id      BIGINT        NOT NULL,
  user_id         BIGINT        NOT NULL,
  parent_id       BIGINT        NULL,
  content         VARCHAR(1000) NOT NULL,
  is_seller_reply TINYINT(1)    NOT NULL DEFAULT 0,
  status          ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_comments_product (product_id, status),
  KEY idx_comments_parent (parent_id),
  CONSTRAINT fk_comments_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  CONSTRAINT fk_comments_user    FOREIGN KEY (user_id)    REFERENCES users(id),
  CONSTRAINT fk_comments_parent  FOREIGN KEY (parent_id)  REFERENCES product_comments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 19. notifications (thông báo trong hệ thống)
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  type       VARCHAR(50)  NOT NULL,     -- ORDER_NEW, ORDER_STATUS, SHOP_APPROVED, SHOP_REJECTED...
  title      VARCHAR(200) NOT NULL,
  content    VARCHAR(500) NULL,
  link_url   VARCHAR(300) NULL,
  is_read    TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notifications_user_read (user_id, is_read, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 20. chat_conversations (chat_key = UUID lưu trong session/cookie)
-- ---------------------------------------------------------------------
CREATE TABLE chat_conversations (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  chat_key        VARCHAR(36) NOT NULL,
  user_id         BIGINT      NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_message_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_key (chat_key),
  KEY idx_chat_user (user_id),
  CONSTRAINT fk_chat_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 21. chat_messages
-- ---------------------------------------------------------------------
CREATE TABLE chat_messages (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  conversation_id       BIGINT       NOT NULL,
  role                  ENUM('USER','BOT') NOT NULL,
  content               TEXT         NOT NULL,
  intent                ENUM('PRODUCT_ADVICE','POLICY','ORDER_LOOKUP','OTHER') NULL,
  suggested_product_ids VARCHAR(255) NULL,   -- "12,45,7"
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chat_messages_conv (conversation_id, created_at),
  CONSTRAINT fk_chat_messages_conv FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

