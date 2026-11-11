-- =====================================================================
-- VietSpecialty - V3 Migration: Chuẩn hóa xuất xứ 30 đặc sản & hình ảnh
-- Đảm bảo giữ đúng 34 tỉnh/thành theo đề tài sắp xếp hành chính 2025
-- =====================================================================

-- 1. Cập nhật tỉnh thành & nơi xuất xứ chi tiết làng nghề (origin_detail)
-- Miền Bắc
UPDATE products SET province_id = 4, origin_detail = 'Đồng Văn, Hà Giang'
WHERE slug = 'mat-ong-rung-hoa-bac-ha';

UPDATE products SET province_id = 4, origin_detail = 'Phia Đén, Nguyên Bình, Cao Bằng'
WHERE slug = 'mien-dong-phia-den-cao-bang';

UPDATE products SET province_id = 4, origin_detail = 'Bảo Lạc, Cao Bằng'
WHERE slug = 'lap-xuong-gac-bep-hun-khoi-mia';

UPDATE products SET province_id = 7, origin_detail = 'Mường Thanh, Điện Biên'
WHERE slug = 'gao-nep-nuong-dien-bien';

UPDATE products SET province_id = 2, origin_detail = 'Cẩm Giàng, Hải Dương'
WHERE slug = 'banh-dau-xanh-rong-vang';

-- Miền Trung
UPDATE products SET province_id = 21, origin_detail = 'Làng Nam Ô, Liên Chiểu, Đà Nẵng'
WHERE slug = 'nuoc-mam-nam-o-ca-com-than';

UPDATE products SET province_id = 18, origin_detail = 'Hương Sơn, Hà Tĩnh'
WHERE slug = 'keo-cu-do-ha-tinh';

UPDATE products SET province_id = 16, origin_detail = 'TP. Thanh Hóa'
WHERE slug = 'nem-chua-thanh-hoa';

-- Miền Nam
UPDATE products SET province_id = 31, origin_detail = 'Châu Thành, Bến Tre'
WHERE slug IN ('keo-dua-sau-rieng-ben-tre', 'buoi-da-xanh-ben-tre');

UPDATE products SET province_id = 30, origin_detail = 'Vũng Thơm, Sóc Trăng'
WHERE slug = 'banh-pia-soc-trang-dau-xanh';

UPDATE products SET province_id = 33, origin_detail = 'Châu Đốc, An Giang'
WHERE slug = 'mam-ca-linh-chau-doc';

UPDATE products SET province_id = 29, origin_detail = 'Gò Dầu, Tây Ninh'
WHERE slug = 'muoi-tom-tay-ninh-thuong-hang';

UPDATE products SET province_id = 34, origin_detail = 'Đảo Ngọc Phú Quốc'
WHERE slug = 'tieu-chin-phu-quoc';

-- 2. Cập nhật hình ảnh đặc sản chính xác món ẩm thực thực tế
UPDATE product_images SET image_url = 'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?w=800&auto=format&fit=crop&q=80'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'keo-dua-sau-rieng-ben-tre');

UPDATE product_images SET image_url = 'https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=800&auto=format&fit=crop&q=80'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'banh-pia-soc-trang-dau-xanh');

UPDATE product_images SET image_url = 'https://images.unsplash.com/photo-1587314168485-3236d6710814?w=800&auto=format&fit=crop&q=80'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'banh-dau-xanh-rong-vang');

UPDATE product_images SET image_url = 'https://images.unsplash.com/photo-1599785209707-a456fc1337bb?w=800&auto=format&fit=crop&q=80'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'keo-cu-do-ha-tinh');

UPDATE product_images SET image_url = 'https://images.unsplash.com/photo-1625944525533-473f1a3d54e7?w=800&auto=format&fit=crop&q=80'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'nem-chua-thanh-hoa');

UPDATE product_images SET image_url = 'https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?w=800&auto=format&fit=crop&q=80'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'kho-ca-sac-ran-u-minh');

UPDATE product_images SET image_url = 'https://images.unsplash.com/photo-1599940824399-b87987ceb72a?w=800&auto=format&fit=crop&q=80'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'tieu-chin-phu-quoc');

UPDATE product_images SET image_url = 'https://images.unsplash.com/photo-1577234286642-fc512a5f8f11?w=800&auto=format&fit=crop&q=80'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'buoi-da-xanh-ben-tre');
