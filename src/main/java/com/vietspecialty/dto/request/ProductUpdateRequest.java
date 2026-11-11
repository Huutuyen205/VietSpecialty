package com.vietspecialty.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank(message = "Tên đặc sản không được để trống")
        @Size(min = 5, max = 255, message = "Tên sản phẩm từ 5 đến 255 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả ngắn tối đa 500 ký tự")
        String shortDescription,

        @NotBlank(message = "Mô tả chi tiết sản phẩm không được để trống")
        String description,

        @NotNull(message = "Giá bán không được để trống")
        @DecimalMin(value = "1000", message = "Giá sản phẩm tối thiểu là 1.000₫")
        BigDecimal price,

        @NotNull(message = "Số lượng tồn kho không được để trống")
        @Min(value = 0, message = "Tồn kho không được âm")
        Integer stock,

        @NotBlank(message = "Đơn vị tính không được để trống")
        String unit,

        @Min(value = 1, message = "Khối lượng phải lớn hơn 0")
        Integer weightGram,

        @NotBlank(message = "Xuất xứ chi tiết không được để trống")
        String originDetail,

        String shelfLife,

        String storageInstructions,

        @Min(value = 3, message = "Hạng OCOP từ 3 đến 5 sao")
        @Max(value = 5, message = "Hạng OCOP từ 3 đến 5 sao")
        Integer ocopRating,

        @NotNull(message = "Vui lòng chọn Tỉnh/Thành phố xuất xứ")
        Integer provinceId,

        @NotNull(message = "Vui lòng chọn Danh mục sản phẩm")
        Integer categoryId
) {}

