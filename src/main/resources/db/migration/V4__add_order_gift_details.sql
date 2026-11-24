ALTER TABLE orders
  ADD COLUMN is_gift             TINYINT(1)    NOT NULL DEFAULT 0 AFTER note,
  ADD COLUMN gift_occasion       VARCHAR(50)   NULL               AFTER is_gift,
  ADD COLUMN gift_wrap_type      VARCHAR(50)   NULL               AFTER gift_occasion,
  ADD COLUMN gift_fee            DECIMAL(15,0) NOT NULL DEFAULT 0 AFTER gift_wrap_type,
  ADD COLUMN gift_recipient_name VARCHAR(100)  NULL               AFTER gift_fee,
  ADD COLUMN gift_message        VARCHAR(300)  NULL               AFTER gift_recipient_name;
