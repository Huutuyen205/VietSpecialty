package com.vietspecialty.config;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final ProvinceRepository provinceRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final VoucherRepository voucherRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(String... args) {
        initAdminAccount();
        initDemoAccountsAndSpecialties();
    }

    private void initAdminAccount() {
        String adminEmail = appProperties.getAdmin().getEmail();
        String adminPassword = appProperties.getAdmin().getPassword();

        if (!userRepository.existsByEmailIgnoreCase(adminEmail)) {
            User admin = User.builder()
                    .fullName("Quản trị viên VietSpecialty")
                    .email(adminEmail.toLowerCase().trim())
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .phone("0988888888")
                    .role(Role.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .build();

            userRepository.save(admin);
            log.info("Initialized default Administrator account: {}", adminEmail);
        }
    }

    private void initDemoAccountsAndSpecialties() {
        if (productRepository.count() >= 10) {
            backfillMissingProductMetadata();
            log.info("Dữ liệu sản phẩm mẫu đã có sẵn ({} sản phẩm), đã đồng bộ searchText.", productRepository.count());
            return;
        }

        log.info("Bắt đầu khởi tạo dữ liệu mẫu 30 Đặc sản OCOP 3 miền...");

        // 1. Khởi tạo tài khoản demo
        String defaultPasswordHash = passwordEncoder.encode("Password123@");

        User buyer = getOrCreateUser("buyer@vietspecialty.vn", "Nguyễn Khách Mua Hàng", "0912345678", Role.USER, defaultPasswordHash);
        User sellerBac = getOrCreateUser("seller_bac@vietspecialty.vn", "Nguyễn Văn Đạt (Trà Tân Cương)", "0988111222", Role.SELLER, defaultPasswordHash);
        User sellerTrung = getOrCreateUser("seller_trung@vietspecialty.vn", "Lê Thị Thảo (Đặc Sản Huế)", "0988333444", Role.SELLER, defaultPasswordHash);
        User sellerNam = getOrCreateUser("seller_nam@vietspecialty.vn", "Trần Út Mười (Đặc Sản Nam Bộ)", "0988555666", Role.SELLER, defaultPasswordHash);

        // Lấy tỉnh thành mẫu
        Province thaiNguyen = getProvinceOrFallback("thai-nguyen");
        Province hue = getProvinceOrFallback("hue");
        Province caMau = getProvinceOrFallback("ca-mau");
        Province sonLa = getProvinceOrFallback("son-la");
        Province quangNgai = getProvinceOrFallback("quang-ngai");
        Province khanhHoa = getProvinceOrFallback("khanh-hoa");
        Province lamDong = getProvinceOrFallback("lam-dong");
        Province dongThap = getProvinceOrFallback("dong-thap");
        Province haNoi = getProvinceOrFallback("ha-noi");

        // Lấy danh mục mẫu
        Category banhMut = getCategoryOrFallback("banh-mut");
        Category traCaPhe = getCategoryOrFallback("tra-ca-phe");
        Category mamGiaVi = getCategoryOrFallback("mam-gia-vi");
        Category haiSanKho = getCategoryOrFallback("hai-san-kho");
        Category thitCheBien = getCategoryOrFallback("thit-che-bien");
        Category traiCayHatSay = getCategoryOrFallback("trai-cay-hat-say");
        Category matOngNongSan = getCategoryOrFallback("mat-ong-nong-san");
        Category ruouDoUong = getCategoryOrFallback("ruou-do-uong");

        // 2. Khởi tạo 3 Gian hàng tiêu biểu 3 miền
        Shop shopBac = getOrCreateShop(sellerBac, "HTX Trà Tân Cương Thái Nguyên", "htx-tra-tan-cuong-thai-nguyen",
                "Chuyên cung cấp trà đinh nõn, trà búp Tân Cương OCOP 5 sao sao suốt thủ công gia truyền.",
                "https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=500&auto=format&fit=crop&q=80",
                thaiNguyen, "Xóm Hồng Thái 2, Xã Tân Cương, TP Thái Nguyên");

        Shop shopTrung = getOrCreateShop(sellerTrung, "Đặc Sản Cố Đô Thiên Hương Huế", "dac-san-co-do-thien-huong-hue",
                "Gìn giữ tinh hoa ẩm thực cung đình Huế: mè xửng giòn, tôm chua gia truyền, trà cung đình tiến vua.",
                "https://images.unsplash.com/photo-1544816155-12df9643f363?w=500&auto=format&fit=crop&q=80",
                hue, "128 Chi Lăng, TP Huế");

        Shop shopNam = getOrCreateShop(sellerNam, "Đặc Sản Miền Tây Út Mười", "dac-san-mien-tay-ut-muoi",
                "Đậm đà hương vị phù sa sông nước Tây Nam Bộ: tôm khô Rạch Gốc, kẹo dừa Bến Tre, rượu sen Đồng Tháp.",
                "https://images.unsplash.com/photo-1596040033229-a9821ebd058d?w=500&auto=format&fit=crop&q=80",
                caMau, "Khóm 1, Thị trấn Rạch Gốc, Huyện Ngọc Hiển, Cà Mau");

        // 3. Khởi tạo 30 Sản phẩm OCOP 3 miền
        // --- MIỀN BẮC (Shop Bắc) ---
        createProductWithImage(shopBac, traCaPhe, thaiNguyen,
                "Trà Đinh Nõn Tân Cương Thượng Hạng", "tra-dinh-non-tan-cuong-thuong-hang",
                new BigDecimal("850000"), 150, "Hộp 500g", 5,
                "Trà Đinh Tân Cương OCOP 5 sao - Đệ nhất danh trà Thái Nguyên hái 1 tôm non",
                "Được tuyển chọn từ những búp chè đinh nõn tươi non nhất vùng đất Tân Cương, sao suốt bằng củi lửa gia truyền giữ trọn hương cốm non thoang thoảng và vị tiền chát ngọt hậu sâu thẳm.",
                "https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, thitCheBien, sonLa,
                "Thịt Trâu Gác Bếp Tây Bắc Chuẩn Vị Mắc Khén", "thit-trau-gac-bep-tay-bac",
                new BigDecimal("420000"), 80, "Gói 500g", 4,
                "Thịt trâu gác bếp Sơn La OCOP 4 sao sấy than củi tẩm mắc khén hạt dổi",
                "Thịt bắp trâu tươi ngon của đồng bào Thái vùng cao, ướp cùng ớt rừng, tỏi, mắc khén rồi gác trên dàn bếp củi thơm ngát khói rơm. Thớ thịt hồng hào, ngọt đậm, thơm cay nồng ấm.",
                "https://images.unsplash.com/photo-1544025162-d76694265947?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, matOngNongSan, thaiNguyen,
                "Mật Ong Rừng Hoa Bạc Hà Cao Nguyên Đá", "mat-ong-rung-hoa-bac-ha",
                new BigDecimal("350000"), 120, "Chai 500ml", 4,
                "Mật ong bạc hà Mèo Vạc OCOP 4 sao màu vàng chanh ánh xanh mát lành",
                "Khai thác từ ong hút mật loài hoa bạc hà dại nở rộ trên cao nguyên đá ở độ cao trên 1.500m. Mật có màu vàng chanh ánh xanh đặc trưng, vị ngọt thanh mát lạnh dịu dàng.",
                "https://images.unsplash.com/photo-1587049352846-4a222e784d38?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, matOngNongSan, thaiNguyen,
                "Miến Dong Phia Đén Cao Bằng Sợi Dai Giòn", "mien-dong-phia-den-cao-bang",
                new BigDecimal("65000"), 200, "Túi 500g", 4,
                "Miến dong Phia Đén Cao Bằng OCOP 4 sao làm từ 100% củ dong riềng đỏ",
                "Sản xuất thủ công trên vùng núi cao mù sương Phia Đén. Sợi miến màu xám tro tự nhiên, khi nấu trong veo, dai giòn sần sật dù đun lại nhiều lần cũng không bao giờ bị bở.",
                "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, thitCheBien, thaiNguyen,
                "Lạp Xưởng Gác Bếp Hun Khói Mía", "lap-xuong-gac-bep-hun-khoi-mia",
                new BigDecimal("180000"), 90, "Túi 500g", 4,
                "Lạp xưởng hun khói bã mía Lạng Sơn thơm nồng gia vị thảo quả",
                "Làm từ thịt nạc vai heo đen bản địa ướp với rượu ngô men lá và gừng núi. Trải qua quy trình hun khói mía chậm giúp lớp vỏ giòn dai, cắn vào béo ngậy mà không hề ngấy.",
                "https://images.unsplash.com/photo-1588168333986-5078d3ae3976?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, banhMut, haNoi,
                "Cốm Mộc Làng Vòng Hà Nội Thơm Hương Sen", "com-moc-lang-vong-ha-noi",
                new BigDecimal("120000"), 100, "Gói 250g", 4,
                "Cốm tươi non gói lá sen truyền thống Hà Thành OCOP 4 sao",
                "Từng hạt cốm nếp cái hoa vàng dẻo thơm hạt ngọc được giã tay khéo léo, bọc trong hai lớp lá: lớp lá ráy giữ ẩm và lớp lá sen thơm ngát thanh tao của mùa thu Hà Nội.",
                "https://images.unsplash.com/photo-1563805042-7684c019e1cb?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, matOngNongSan, haNoi,
                "Tỏi Đen Một Nhánh Cô Đơn Học Viện Quân Y", "toi-den-mot-nhanh-co-don",
                new BigDecimal("290000"), 110, "Hộp 500g", 5,
                "Tỏi đen cô đơn lên men tự nhiên 60 ngày giàu hoạt chất S-allyl-cysteine",
                "Lên men tự nhiên theo công nghệ Nhật Bản từ tỏi cô đơn Hải Dương. Tép tỏi dẻo quánh như thạch quả sấy, ngọt thanh không cay nồng, hỗ trợ tăng cường đề kháng vượt trội.",
                "https://images.unsplash.com/photo-1615485290382-441e4d049cb5?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, matOngNongSan, thaiNguyen,
                "Gạo Nếp Nương Điện Biên Hạt Tròn Dẻo", "gao-nep-nuong-dien-bien",
                new BigDecimal("55000"), 300, "Túi 1kg", 4,
                "Gạo nếp nương Mường Thanh Điện Biên thơm lừng dẻo quánh",
                "Gieo trồng trên những thửa ruộng bậc thang đón nắng gió đại ngàn. Hạt gạo căng mẩy, khi đồ xôi tỏa hương thơm nồng nàn, để nguội vẫn dẻo quánh thơm ngon.",
                "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, banhMut, thaiNguyen,
                "Bánh Đậu Xanh Rồng Vàng Nguyên Chất", "banh-dau-xanh-rong-vang",
                new BigDecimal("45000"), 250, "Hộp 300g", 4,
                "Bánh đậu xanh truyền thống Hải Dương tan ngay đầu lưỡi",
                "Đậu xanh lòng vàng rang thơm mịn kết hợp cùng đường kính tinh luyện và tinh dầu hoa bưởi thoang thoảng. Thưởng thức cùng chén trà Tân Cương là phong vị tuyệt mỹ tao nhã.",
                "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopBac, ruouDoUong, sonLa,
                "Rượu Mơ Rừng Mộc Châu Ngâm Đường Phèn", "ruou-mo-rung-moc-chau",
                new BigDecimal("195000"), 85, "Chai 500ml", 4,
                "Rượu mơ má đào ngâm ủ men lá truyền thống êm dịu ngọt mát",
                "Quả mơ má đào chín mọng hái từ thung lũng Mộc Châu ngâm ủ thủ công cùng rượu nếp men lá hơn 12 tháng. Vị chua ngọt cân bằng, hậu vị êm dịu nồng đượm tình Tây Bắc.",
                "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=800&auto=format&fit=crop&q=80");

        // --- MIỀN TRUNG & TÂY NGUYÊN (Shop Trung) ---
        createProductWithImage(shopTrung, banhMut, hue,
                "Mè Xửng Giòn Cố Đô Huế Truyền Thống", "me-xung-gion-co-do-hue",
                new BigDecimal("50000"), 200, "Gói 300g", 4,
                "Mè xửng giòn kẹp bánh tráng nướng vàng rụm xứ Huế OCOP 4 sao",
                "Mạch nha dẻo quánh nấu cùng đậu phộng bùi béo và mè rang thơm lừng, ép giữa hai lớp bánh tráng nướng giòn tan. Món quà ngọt ngào hoài niệm của mảnh đất Thần Kinh.",
                "https://images.unsplash.com/photo-1544816155-12df9643f363?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, traCaPhe, hue,
                "Trà Cung Đình Huế Nhất Dạ Đế Vương", "tra-cung-dinh-hue-nhat-da-de-vuong",
                new BigDecimal("110000"), 140, "Gói 500g", 5,
                "Trà thảo mộc cung đình 16 vị thảo dược quý tiến vua triều Nguyễn OCOP 5 sao",
                "Bài thuốc ngự y cổ truyền kết hợp atiso, cúc hoa, cỏ ngọt, tim sen, đại táo, đẳng sâm... Giúp thanh nhiệt giải độc, an thần ngủ ngon giấc và điều hòa huyết áp tuyệt vời.",
                "https://images.unsplash.com/photo-1597481499750-3e6b22637e12?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, mamGiaVi, hue,
                "Tôm Chua Gia Truyền Cố Đô Huế", "tom-chua-gia-truyen-co-do-hue",
                new BigDecimal("85000"), 160, "Hũ 450g", 4,
                "Tôm chua nước mắm nhĩ ủ măng vòi riềng tỏi ớt OCOP 4 sao",
                "Tôm sông tươi sống làm sạch ướp rượu trắng rồi ngâm lên men chua ngọt cùng măng vòi, riềng củ và ớt cay nồng. Ăn kèm thịt ba chỉ luộc và rau sống bún tươi ngon hết ý.",
                "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, mamGiaVi, hue,
                "Nước Mắm Nam Ô Truyền Thống Cá Cơm Than", "nuoc-mam-nam-o-ca-com-than",
                new BigDecimal("160000"), 180, "Chai 500ml", 5,
                "Nước mắm Nam Ô Đà Nẵng OCOP 5 sao ủ chum sành muối hạt Sa Huỳnh",
                "Làng nghề nước mắm Nam Ô hơn 400 năm tuổi. Chỉ dùng cá cơm than đánh bắt tháng Ba âm lịch ủ ròng rã 12 tháng trong chum sành. Giọt mắm đỏ cánh gián đậm đà đạm tự nhiên.",
                "https://images.unsplash.com/photo-1589302168068-964664d93dc0?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, mamGiaVi, quangNgai,
                "Tỏi Mồ Côi Đảo Lý Sơn Thượng Hạng", "toi-mo-coi-dao-ly-son",
                new BigDecimal("480000"), 60, "Hộp 500g", 5,
                "Tỏi cô đơn Lý Sơn OCOP 5 sao bồi đắp từ nham thạch núi lửa và cát san hô",
                "Mỗi cây tỏi chỉ cho duy nhất một tép tròn trĩnh, hội tụ toàn bộ tinh túy đất trời biển đảo Lý Sơn. Vị cay thanh, không hăng gắt, hàm lượng tinh dầu kháng khuẩn cực cao.",
                "https://images.unsplash.com/photo-1615485290382-441e4d049cb5?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, haiSanKho, khanhHoa,
                "Yến Sào Tinh Chế Đảo Nha Trang Khánh Hòa", "yen-sao-tinh-che-khanh-hoa",
                new BigDecimal("2400000"), 35, "Hộp 50g", 5,
                "Tổ yến đảo thiên nhiên tinh chế sợi nguyên bản OCOP 5 sao quốc gia",
                "Khai thác từ các hang đảo vách đá tự nhiên vùng biển Khánh Hòa. Được làm sạch lông thủ công tỉ mỉ bằng nước tinh khiết, sợi yến dai giòn, nở bung nhiều khi chưng cách thủy.",
                "https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, traCaPhe, lamDong,
                "Cà Phê Arabica Cầu Đất Lâm Đồng Rang Mộc", "ca-phe-arabica-cau-dat",
                new BigDecimal("175000"), 150, "Túi 500g", 5,
                "Cà phê Arabica Cầu Đất Đà Lạt độ cao 1650m hương hoa quả chua thanh",
                "Được ví như nữ hoàng cà phê Việt Nam với khí hậu lạnh sương mù quanh năm. Hạt cà phê hái chín 100%, rang mộc thơm nồng quyến rũ với hậu vị ngọt sâu lắng say đắm lòng người.",
                "https://images.unsplash.com/photo-1559056199-641a0ac8b55e?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, traiCayHatSay, lamDong,
                "Hạt Mắc Ca Sấy Nứt Vỏ Tây Nguyên Cao Cấp", "hat-mac-ca-say-nut-vo",
                new BigDecimal("165000"), 120, "Hộp 500g", 4,
                "Mắc ca Đắk Lắk OCOP 4 sao bùi béo giàu Omega 3-6-9 tặng kèm dụng cụ bóc",
                "Nữ hoàng các loại hạt khô giàu dinh dưỡng cho mẹ bầu và trẻ nhỏ. Sấy gió giữ trọn vị giòn rụm béo ngậy ngọt bùi tự nhiên không qua ướp tẩm hóa chất.",
                "https://images.unsplash.com/photo-1536591375315-1b8e7225d827?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, banhMut, hue,
                "Kẹo Cu Đơ Hà Tĩnh Đậm Vị Gừng Cay Nồng", "keo-cu-do-ha-tinh",
                new BigDecimal("55000"), 190, "Hộp 5 cái", 3,
                "Bánh cu đơ Hà Tĩnh mật mía bọc đậu phộng rang giòn thơm lừng",
                "Mật mía đun sôi cùng gừng tươi giã nhỏ và đậu phộng ta hạt mẩy, kẹp giữa hai lát bánh tráng nướng vừng đen giòn tan. Cắn một miếng thơm nồng sưởi ấm mùa đông rét mướt.",
                "https://images.unsplash.com/photo-1544816155-12df9643f363?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopTrung, thitCheBien, hue,
                "Nem Chua Thanh Hóa Vị Chua Cay Giòn Bì", "nem-chua-thanh-hoa",
                new BigDecimal("60000"), 130, "Bó 10 chiếc", 3,
                "Nem chua Thanh Hóa cuốn lá chuối xanh lên men tự nhiên giòn dai",
                "Thịt mông heo nóng hổi xay nhuyễn trộn bì heo cạo mỏng tang, nêm nếm thính gạo rang vàng, tỏi tươi và lá đinh lăng xanh biếc. Vị chua dịu giòn sần sật khó cưỡng.",
                "https://images.unsplash.com/photo-1544025162-d76694265947?w=800&auto=format&fit=crop&q=80");

        // --- MIỀN NAM (Shop Nam) ---
        createProductWithImage(shopNam, haiSanKho, caMau,
                "Tôm Khô Sinh Thái Rạch Gốc Cà Mau", "tom-kho-sinh-thai-rach-goc",
                new BigDecimal("680000"), 75, "Hộp 500g", 5,
                "Tôm khô rừng ngập mặn Cà Mau OCOP 5 sao thịt đỏ au ngọt tự nhiên",
                "Được làm từ con tôm đất sống tự nhiên dưới tán rừng đước Cà Mau. Luộc nước muối đúng độ rồi phơi đủ nắng giòn tan, thịt tôm đỏ au săn chắc, ngọt thanh đậm đà.",
                "https://images.unsplash.com/photo-1534482421-64566f976cfa?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, haiSanKho, caMau,
                "Khô Cá Sặc Rằn Bổi U Minh Một Nắng", "kho-ca-sac-ran-u-minh",
                new BigDecimal("190000"), 95, "Túi 500g", 4,
                "Khô cá sặc bổi Cà Mau xẻ phơi tự nhiên thịt dày béo ngậy",
                "Cá sặc rằn tươi sống vùng rừng tràm U Minh béo ngậy được tẩm ướp vừa miệng rồi phơi nắng gắt. Đem nướng hoặc chiên giòn trộn gỏi xoài chua thì tốn cơm vô cùng.",
                "https://images.unsplash.com/photo-1534482421-64566f976cfa?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, banhMut, caMau,
                "Kẹo Dừa Sầu Riêng Bến Tre Mạch Nha", "keo-dua-sau-rieng-ben-tre",
                new BigDecimal("40000"), 220, "Hộp 300g", 4,
                "Kẹo dừa truyền thống mỏ cày dẻo quánh thơm nức hương sầu riêng",
                "Nước cốt dừa xiêm béo ngậy hòa quyện cùng cơm sầu riêng chín cây và mạch nha nếp thơm ngọt. Từng viên kẹo bọc giấy gạo tan chảy êm ái trên đầu lưỡi thân thương.",
                "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, banhMut, caMau,
                "Bánh Pía Sóc Trăng Đậu Xanh Trứng Muối", "banh-pia-soc-trang-dau-xanh",
                new BigDecimal("75000"), 180, "Túi 4 bánh", 4,
                "Bánh pía da ngàn lớp thơm nức sầu riêng bùi béo trứng muối",
                "Lớp vỏ ngàn lớp mỏng tang xếp lớp bao bọc lấy nhân đậu xanh xay nhuyễn mịn màng, sầu riêng tươi thơm lừng cùng lòng đỏ trứng muối béo bùi ngất ngây.",
                "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, mamGiaVi, caMau,
                "Mắm Cá Linh Châu Đốc An Giang Gia Truyền", "mam-ca-linh-chau-doc",
                new BigDecimal("80000"), 110, "Hũ 500g", 4,
                "Mắm cá linh mùa nước nổi ủ đường thốt nốt thơm lừng xứ Bảy Núi",
                "Cá linh non đầu mùa béo mẫm được ủ thính gạo và chao đường thốt nốt nguyên chất. Dùng để kho lẩu mắm miền Tây thơm lừng nức mũi ai ăn một lần cũng nhớ mãi.",
                "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, mamGiaVi, caMau,
                "Muối Tôm Tây Ninh Thượng Hạng Cay Nồng", "muoi-tom-tay-ninh-thuong-hang",
                new BigDecimal("55000"), 240, "Hũ 250g", 3,
                "Muối tôm Tây Ninh rang hạt vàng ruộm chấm hoa quả tuyệt đỉnh",
                "Sự hòa quyện tài tình giữa muối hột sạch, tôm khô giã nhuyễn, ớt đỏ cay nồng và tỏi thơm phức. Là linh hồn chấm các loại quả trái cây chua ngọt nức tiếng Nam Bộ.",
                "https://images.unsplash.com/photo-1589302168068-964664d93dc0?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, ruouDoUong, dongThap,
                "Rượu Sen Đồng Tháp Mười Hạ Thổ Men Hồng", "ruou-sen-dong-thap-muoi",
                new BigDecimal("230000"), 70, "Bình gốm 500ml", 4,
                "Rượu cất từ hạt củ sen và nhụy sen thơm thanh khiết xứ sen hồng",
                "Được nấu theo phương pháp gia truyền kết hợp hạt sen, củ sen và tim sen cùng men bột vỏ trấu. Hạ thổ hơn 12 tháng mang đến hương vị thanh khiết, êm ái ngây ngất.",
                "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, traiCayHatSay, dongThap,
                "Xoài Cát Chu Sấy Dẻo Cao Lãnh Không Đường", "xoai-cat-chu-say-deo",
                new BigDecimal("95000"), 160, "Túi 250g", 4,
                "Xoài sấy dẻo công nghệ lạnh giữ nguyên màu vàng óng ngọt đậm đà",
                "Từ những trái xoài Cát Chu chín cây trứ danh Đồng Tháp. Công nghệ sấy dẻo nhiệt độ thấp giữ trọn vẹn vitamin, chất xơ và hương vị ngọt thanh tự nhiên của trái cây tươi.",
                "https://images.unsplash.com/photo-1553279768-865429fa0078?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, mamGiaVi, caMau,
                "Tiêu Chín Phú Quốc Hạt Đỏ Thơm Nồng Cay", "tieu-chin-phu-quoc",
                new BigDecimal("145000"), 130, "Hũ 200g", 5,
                "Tiêu chín đỏ Phú Quốc OCOP 5 sao hạt mẩy tròn cay nồng nàn",
                "Hạt tiêu được hái từng chùm khi đã chín đỏ mọng trên cành, phơi nắng giòn hạt. Thơm cay nồng đậm hơn hẳn tiêu thông thường, làm dậy mùi thơm ngất ngây cho mọi món ăn.",
                "https://images.unsplash.com/photo-1596040033229-a9821ebd058d?w=800&auto=format&fit=crop&q=80");

        createProductWithImage(shopNam, traiCayHatSay, caMau,
                "Bưởi Da Xanh Bến Tre Ruột Hồng Ngọt Lịm", "buoi-da-xanh-ben-tre",
                new BigDecimal("85000"), 90, "Quả 1.5kg", 5,
                "Bưởi da xanh Bến Tre tép mọng nước giòn rụm không hột OCOP 5 sao",
                "Trái bưởi da xanh mướt, vỏ mỏng dính, múi bưởi màu hồng tươi mọng nước, vị ngọt thanh mát lịm sảng khoái không có vị chua hay đắng chát.",
                "https://images.unsplash.com/photo-1557800636-894a64c1696f?w=800&auto=format&fit=crop&q=80");

        // 4. Khởi tạo Vouchers mẫu
        initSampleVouchers(sellerBac, sellerTrung, sellerNam, shopBac, shopTrung, shopNam);

        log.info("Khởi tạo thành công 30 Đặc sản OCOP 3 miền và 3 gian hàng chính hãng!");
    }

    private void initSampleVouchers(User sellerBac, User sellerTrung, User sellerNam, Shop shopBac, Shop shopTrung, Shop shopNam) {
        if (!voucherRepository.existsByCodeIgnoreCase("VIETSPECIALTY")) {
            voucherRepository.save(Voucher.builder()
                    .code("VIETSPECIALTY")
                    .name("Khai xuân đắc lộc - Giảm 50.000đ toàn sàn")
                    .scope(VoucherScope.PLATFORM)
                    .discountType(DiscountType.FIXED)
                    .discountValue(BigDecimal.valueOf(50000))
                    .minOrderValue(BigDecimal.valueOf(300000))
                    .usageLimit(1000)
                    .perUserLimit(2)
                    .startAt(LocalDateTime.now())
                    .endAt(LocalDateTime.now().plusMonths(6))
                    .isActive(true)
                    .createdBy(sellerBac)
                    .build());
        }

        if (!voucherRepository.existsByCodeIgnoreCase("FREESHIP30")) {
            voucherRepository.save(Voucher.builder()
                    .code("FREESHIP30")
                    .name("Hỗ trợ phí vận chuyển đặc sản 30.000đ")
                    .scope(VoucherScope.PLATFORM)
                    .discountType(DiscountType.FIXED)
                    .discountValue(BigDecimal.valueOf(30000))
                    .minOrderValue(BigDecimal.valueOf(200000))
                    .usageLimit(1000)
                    .perUserLimit(3)
                    .startAt(LocalDateTime.now())
                    .endAt(LocalDateTime.now().plusMonths(6))
                    .isActive(true)
                    .createdBy(sellerBac)
                    .build());
        }

        if (!voucherRepository.existsByCodeIgnoreCase("TANCUONG10")) {
            voucherRepository.save(Voucher.builder()
                    .code("TANCUONG10")
                    .name("Ưu đãi 10% danh trà Thái Nguyên")
                    .scope(VoucherScope.SHOP)
                    .shop(shopBac)
                    .discountType(DiscountType.PERCENT)
                    .discountValue(BigDecimal.valueOf(10))
                    .maxDiscount(BigDecimal.valueOf(100000))
                    .minOrderValue(BigDecimal.valueOf(200000))
                    .usageLimit(200)
                    .perUserLimit(1)
                    .startAt(LocalDateTime.now())
                    .endAt(LocalDateTime.now().plusMonths(3))
                    .isActive(true)
                    .createdBy(sellerBac)
                    .build());
        }
    }

    private User getOrCreateUser(String email, String fullName, String phone, Role role, String passwordHash) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email.toLowerCase().trim())
                        .fullName(fullName)
                        .phone(phone)
                        .role(role)
                        .passwordHash(passwordHash)
                        .status(UserStatus.ACTIVE)
                        .build()));
    }

    private Shop getOrCreateShop(User owner, String name, String slug, String description, String logoUrl, Province province, String address) {
        return shopRepository.findBySlug(slug)
                .orElseGet(() -> shopRepository.save(Shop.builder()
                        .name(name)
                        .slug(slug)
                        .owner(owner)
                        .description(description)
                        .logoUrl(logoUrl)
                        .province(province)
                        .addressDetail(address)
                        .phone(owner.getPhone())
                        .status(ShopStatus.APPROVED)
                        .build()));
    }

    private Province getProvinceOrFallback(String slug) {
        return provinceRepository.findBySlug(slug)
                .orElseGet(() -> provinceRepository.findAll().get(0));
    }

    private Category getCategoryOrFallback(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseGet(() -> categoryRepository.findAll().get(0));
    }

    private void createProductWithImage(Shop shop, Category category, Province province,
                                        String name, String slug, BigDecimal price, int stock,
                                        String unit, int ocopRating, String shortDesc, String desc,
                                        String imageUrl) {
        if (productRepository.findBySlugAndStatus(slug, ProductStatus.ACTIVE).isPresent()) {
            return;
        }

        String originDetail = "Làng nghề truyền thống " + (province != null ? province.getName() : "Việt Nam");
        String searchText = com.vietspecialty.util.VietnameseTextUtils.toSearchableText(
                name,
                shortDesc,
                originDetail,
                province != null ? province.getName() : "",
                category != null ? category.getName() : ""
        );

        Product product = Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name(name)
                .slug(slug)
                .price(price)
                .stock(stock)
                .soldCount(15 + (int)(Math.random() * 85))
                .avgRating(BigDecimal.valueOf(4.8 + Math.random() * 0.2))
                .reviewCount(10 + (int)(Math.random() * 40))
                .unit(unit)
                .weightGram(500)
                .originDetail(originDetail)
                .shelfLife("12 tháng kể từ ngày sản xuất")
                .storageInstructions("Bảo quản nơi khô ráo, thoáng mát, tránh ánh nắng trực tiếp")
                .ocopRating(ocopRating)
                .status(ProductStatus.ACTIVE)
                .shortDescription(shortDesc)
                .description(desc)
                .searchText(searchText)
                .build();

        Product savedProduct = productRepository.save(product);

        ProductImage image = ProductImage.builder()
                .product(savedProduct)
                .imageUrl(imageUrl)
                .isPrimary(true)
                .sortOrder(0)
                .build();

        productImageRepository.save(image);
    }

    private void backfillMissingProductMetadata() {
        List<Product> products = productRepository.findAll();
        for (Product p : products) {
            boolean changed = false;
            String provName = p.getProvince() != null ? p.getProvince().getName() : "Việt Nam";
            String catName = p.getCategory() != null ? p.getCategory().getName() : "";
            if (p.getOriginDetail() == null || p.getOriginDetail().isBlank()) {
                p.setOriginDetail("Làng nghề truyền thống " + provName);
                changed = true;
            }
            if (p.getWeightGram() == null) {
                p.setWeightGram(500);
                changed = true;
            }
            if (p.getShelfLife() == null || p.getShelfLife().isBlank()) {
                p.setShelfLife("12 tháng kể từ ngày sản xuất");
                changed = true;
            }
            if (p.getStorageInstructions() == null || p.getStorageInstructions().isBlank()) {
                p.setStorageInstructions("Bảo quản nơi khô ráo, thoáng mát, tránh ánh nắng trực tiếp");
                changed = true;
            }
            if (p.getSearchText() == null || p.getSearchText().isBlank()) {
                p.setSearchText(com.vietspecialty.util.VietnameseTextUtils.toSearchableText(
                        p.getName(), p.getShortDescription(), p.getOriginDetail(), provName, catName
                ));
                changed = true;
            }
            if (changed) {
                productRepository.save(p);
            }
        }
    }
}
