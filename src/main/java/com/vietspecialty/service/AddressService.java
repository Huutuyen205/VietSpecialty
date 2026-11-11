package com.vietspecialty.service;

import com.vietspecialty.dto.request.AddressRequest;
import com.vietspecialty.dto.response.AddressResponse;

import java.util.List;

public interface AddressService {

    List<AddressResponse> getUserAddresses(Long userId);

    AddressResponse getAddressById(Long id, Long userId);

    AddressResponse createAddress(Long userId, AddressRequest request);

    AddressResponse updateAddress(Long id, Long userId, AddressRequest request);

    void setDefaultAddress(Long id, Long userId);

    void deleteAddress(Long id, Long userId);
}

