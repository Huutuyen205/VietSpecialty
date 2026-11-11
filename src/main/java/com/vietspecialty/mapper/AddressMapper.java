package com.vietspecialty.mapper;

import com.vietspecialty.domain.entity.Address;
import com.vietspecialty.dto.response.AddressResponse;

public final class AddressMapper {

    private AddressMapper() {}

    public static AddressResponse toResponse(Address a) {
        if (a == null) return null;

        String provinceName = a.getProvince() != null ? a.getProvince().getName() : "";
        String fullAddress = a.getStreetAddress() + ", " + a.getWard() + ", " + provinceName;

        return new AddressResponse(
                a.getId(),
                a.getReceiverName(),
                a.getReceiverPhone(),
                a.getProvince() != null ? a.getProvince().getId() : null,
                provinceName,
                a.getWard(),
                a.getStreetAddress(),
                fullAddress,
                a.getIsDefault()
        );
    }
}

