package com.vietspecialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Tên người nhận không được để trống")
        @Size(max = 100, message = "Tên người nhận không vượt quá 100 ký tự")
        String receiverName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$", message = "Số điện thoại không đúng định dạng Việt Nam")
        String receiverPhone,

        @NotNull(message = "Vui lòng chọn Tỉnh/Thành phố")
        Integer provinceId,

        @NotBlank(message = "Phường/Xã không được để trống")
        @Size(max = 150, message = "Phường/Xã không vượt quá 150 ký tự")
        String ward,

        @NotBlank(message = "Địa chỉ chi tiết (số nhà, tên đường...) không được để trống")
        @Size(max = 255, message = "Địa chỉ chi tiết không vượt quá 255 ký tự")
        String streetAddress,

        Boolean isDefault
) {
}

