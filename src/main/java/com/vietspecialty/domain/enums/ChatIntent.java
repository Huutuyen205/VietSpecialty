package com.vietspecialty.domain.enums;

public enum ChatIntent {
    PRODUCT_ADVICE("Tư vấn & Khám phá Đặc sản"),
    POLICY("Chính sách, Phí vận chuyển & Ưu đãi"),
    ORDER_LOOKUP("Tra cứu & Theo dõi Đơn hàng"),
    OTHER("Trò chuyện & Hỗ trợ trong khuôn khổ VietSpecialty"),
    GIFT_ADVICE("Tư vấn Quà biếu Đặc sản"),
    FOOD_PAIRING("Tư vấn Kết hợp Món ăn & Thức chấm"),
    CULINARY_CULTURE("Văn hóa Ẩm thực & Nguồn gốc Làng nghề"),
    RECIPE_STORAGE("Cách chế biến & Bảo quản Đặc sản"),
    MENU_PLANNING("Gợi ý Thực đơn & Mâm cơm Gia đình");

    private final String description;

    ChatIntent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
