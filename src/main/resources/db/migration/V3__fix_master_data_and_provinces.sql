-- =====================================================================
-- VietSpecialty - V3 Migration: Bổ sung tỉnh thành đặc sản & chuẩn hóa dữ liệu
-- =====================================================================

-- 1. Thêm các tỉnh thành sản sinh đặc sản OCOP trọng điểm chưa có trong V2
INSERT INTO provinces (region_id, name, slug)
SELECT 4, 'Bến Tre', 'ben-tre'
WHERE NOT EXISTS (SELECT 1 FROM provinces WHERE slug = 'ben-tre');

INSERT INTO provinces (region_id, name, slug)
SELECT 4, 'Sóc Trăng', 'soc-trang'
WHERE NOT EXISTS (SELECT 1 FROM provinces WHERE slug = 'soc-trang');

INSERT INTO provinces (region_id, name, slug)
SELECT 4, 'Kiên Giang', 'kien-giang'
WHERE NOT EXISTS (SELECT 1 FROM provinces WHERE slug = 'kien-giang');

INSERT INTO provinces (region_id, name, slug)
SELECT 1, 'Hà Giang', 'ha-giang'
WHERE NOT EXISTS (SELECT 1 FROM provinces WHERE slug = 'ha-giang');

INSERT INTO provinces (region_id, name, slug)
SELECT 1, 'Hải Dương', 'hai-duong'
WHERE NOT EXISTS (SELECT 1 FROM provinces WHERE slug = 'hai-duong');

-- 2. Cập nhật lại tỉnh thành chính xác cho 30 đặc sản
-- Miền Bắc
UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'ha-giang' LIMIT 1)
WHERE slug = 'mat-ong-rung-hoa-bac-ha';

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'cao-bang' LIMIT 1)
WHERE slug IN ('mien-dong-phia-den-cao-bang', 'lap-xuong-gac-bep-hun-khoi-mia');

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'dien-bien' LIMIT 1)
WHERE slug = 'gao-nep-nuong-dien-bien';

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'hai-duong' LIMIT 1)
WHERE slug = 'banh-dau-xanh-rong-vang';

-- Miền Trung
UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'da-nang' LIMIT 1)
WHERE slug = 'nuoc-mam-nam-o-ca-com-than';

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'ha-tinh' LIMIT 1)
WHERE slug = 'keo-cu-do-ha-tinh';

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'thanh-hoa' LIMIT 1)
WHERE slug = 'nem-chua-thanh-hoa';

-- Miền Nam
UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'ben-tre' LIMIT 1)
WHERE slug IN ('keo-dua-sau-rieng-ben-tre', 'buoi-da-xanh-ben-tre');

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'soc-trang' LIMIT 1)
WHERE slug = 'banh-pia-soc-trang-dau-xanh';

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'an-giang' LIMIT 1)
WHERE slug = 'mam-ca-linh-chau-doc';

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'tay-ninh' LIMIT 1)
WHERE slug = 'muoi-tom-tay-ninh-thuong-hang';

UPDATE products SET province_id = (SELECT id FROM provinces WHERE slug = 'kien-giang' LIMIT 1)
WHERE slug = 'tieu-chin-phu-quoc';

-- 3. Cập nhật hình ảnh đặc sản khớp đúng món ẩm thực thực tế
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
