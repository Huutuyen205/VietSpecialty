-- =====================================================================
-- VietSpecialty - Master Data (Vùng miền, 34 Tỉnh/Thành 2025, 8 Danh mục)
-- =====================================================================

INSERT INTO regions (id, name, slug, description, color_hex, sort_order) VALUES
 (1, 'Miền Bắc',   'mien-bac',   'Đặc sản trung du, miền núi và đồng bằng Bắc Bộ', '#0F9D8A', 1),
 (2, 'Miền Trung', 'mien-trung', 'Đặc sản Bắc Trung Bộ và duyên hải miền Trung',   '#E9A21F', 2),
 (3, 'Tây Nguyên', 'tay-nguyen', 'Cà phê, mắc ca, hồ tiêu và nông sản cao nguyên', '#8B5E3C', 3),
 (4, 'Miền Nam',   'mien-nam',   'Đặc sản Đông Nam Bộ và đồng bằng sông Cửu Long', '#E8704A', 4);

INSERT INTO provinces (region_id, name, slug) VALUES
 (1,'Hà Nội','ha-noi'),(1,'Hải Phòng','hai-phong'),(1,'Quảng Ninh','quang-ninh'),
 (1,'Cao Bằng','cao-bang'),(1,'Lạng Sơn','lang-son'),(1,'Lai Châu','lai-chau'),
 (1,'Điện Biên','dien-bien'),(1,'Sơn La','son-la'),(1,'Tuyên Quang','tuyen-quang'),
 (1,'Lào Cai','lao-cai'),(1,'Thái Nguyên','thai-nguyen'),(1,'Phú Thọ','phu-tho'),
 (1,'Bắc Ninh','bac-ninh'),(1,'Hưng Yên','hung-yen'),(1,'Ninh Bình','ninh-binh'),
 (2,'Thanh Hóa','thanh-hoa'),(2,'Nghệ An','nghe-an'),(2,'Hà Tĩnh','ha-tinh'),
 (2,'Quảng Trị','quang-tri'),(2,'Huế','hue'),(2,'Đà Nẵng','da-nang'),
 (2,'Quảng Ngãi','quang-ngai'),(2,'Khánh Hòa','khanh-hoa'),
 (3,'Gia Lai','gia-lai'),(3,'Đắk Lắk','dak-lak'),(3,'Lâm Đồng','lam-dong'),
 (4,'TP. Hồ Chí Minh','tp-ho-chi-minh'),(4,'Đồng Nai','dong-nai'),(4,'Tây Ninh','tay-ninh'),
 (4,'Cần Thơ','can-tho'),(4,'Vĩnh Long','vinh-long'),(4,'Đồng Tháp','dong-thap'),
 (4,'An Giang','an-giang'),(4,'Cà Mau','ca-mau');

INSERT INTO categories (name, slug, icon, sort_order) VALUES
 ('Bánh & Mứt',             'banh-mut',          'cake',      1),
 ('Trà & Cà phê',           'tra-ca-phe',        'coffee',    2),
 ('Mắm & Gia vị',           'mam-gia-vi',        'soup',      3),
 ('Hải sản khô',            'hai-san-kho',       'fish',      4),
 ('Thịt & Chế biến',        'thit-che-bien',     'beef',      5),
 ('Trái cây & Hạt sấy',     'trai-cay-hat-say',  'nut',       6),
 ('Mật ong & Nông sản',     'mat-ong-nong-san',  'wheat',     7),
 ('Rượu & Đồ uống',         'ruou-do-uong',      'wine',      8);

