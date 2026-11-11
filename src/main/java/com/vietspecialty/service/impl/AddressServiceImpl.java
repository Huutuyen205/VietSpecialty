package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.Address;
import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.dto.request.AddressRequest;
import com.vietspecialty.dto.response.AddressResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.AddressRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final ProvinceRepository provinceRepository;
    private final UserRepository userRepository;

    @Override
    public List<AddressResponse> getUserAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AddressResponse getAddressById(Long id, Long userId) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        return toResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        long currentCount = addressRepository.countByUserId(userId);
        if (currentCount >= 10) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bạn chỉ được lưu tối đa 10 địa chỉ nhận hàng");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Province province = provinceRepository.findById(request.provinceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Tỉnh/Thành phố không hợp lệ"));

        boolean shouldBeDefault = currentCount == 0 || Boolean.TRUE.equals(request.isDefault());
        if (shouldBeDefault) {
            addressRepository.resetDefaultAddressForUser(userId);
        }

        Address address = Address.builder()
                .user(user)
                .receiverName(request.receiverName().trim())
                .receiverPhone(request.receiverPhone().trim())
                .province(province)
                .ward(request.ward().trim())
                .streetAddress(request.streetAddress().trim())
                .isDefault(shouldBeDefault)
                .build();

        Address saved = addressRepository.save(address);
        log.info("Created address id={} for user id={}", saved.getId(), userId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(Long id, Long userId, AddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        Province province = provinceRepository.findById(request.provinceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Tỉnh/Thành phố không hợp lệ"));

        if (Boolean.TRUE.equals(request.isDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            addressRepository.resetDefaultAddressForUser(userId);
            address.setIsDefault(true);
        }

        address.setReceiverName(request.receiverName().trim());
        address.setReceiverPhone(request.receiverPhone().trim());
        address.setProvince(province);
        address.setWard(request.ward().trim());
        address.setStreetAddress(request.streetAddress().trim());

        Address updated = addressRepository.save(address);
        log.info("Updated address id={} for user id={}", updated.getId(), userId);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void setDefaultAddress(Long id, Long userId) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        addressRepository.resetDefaultAddressForUser(userId);
        address.setIsDefault(true);
        addressRepository.save(address);
        log.info("Set default address id={} for user id={}", id, userId);
    }

    @Override
    @Transactional
    public void deleteAddress(Long id, Long userId) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        addressRepository.delete(address);
        log.info("Deleted address id={} for user id={}", id, userId);

        // Nếu vừa xóa địa chỉ mặc định mà vẫn còn địa chỉ khác, chọn địa chỉ đầu tiên làm mặc định
        if (wasDefault) {
            List<Address> remaining = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
            if (!remaining.isEmpty()) {
                Address newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                addressRepository.save(newDefault);
                log.info("Promoted address id={} to default for user id={}", newDefault.getId(), userId);
            }
        }
    }

    private AddressResponse toResponse(Address a) {
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

