package com.vietspecialty.dto.response;

public record AddressResponse(
        Long id,
        String receiverName,
        String receiverPhone,
        Integer provinceId,
        String provinceName,
        String ward,
        String streetAddress,
        String fullAddress,
        Boolean isDefault
) {
    public String recipientName() {
        return receiverName;
    }

    public String phone() {
        return receiverPhone;
    }
}

