package com.vietspecialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShopUpdateRequest(
        @NotBlank(message = "Tên gian hàng không được để trống")
        @Size(min = 3, max = 100, message = "Tên gian hàng phải từ 3 đến 100 ký tự")
        String name,

        @Size(max = 1000, message = "Mô tả gian hàng tối đa 1000 ký tự")
        String description,

        @NotBlank(message = "Địa chỉ lấy hàng không được để trống")
        String pickupAddress,

        @NotBlank(message = "Số điện thoại hotline gian hàng không được để trống")
        String hotline
) {}

