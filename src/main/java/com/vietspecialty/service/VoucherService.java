package com.vietspecialty.service;

import com.vietspecialty.domain.entity.Voucher;
import com.vietspecialty.dto.request.VoucherCreateRequest;
import com.vietspecialty.dto.response.VoucherResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface VoucherService {

    Voucher validateAndGetVoucher(String code, Long shopId, BigDecimal orderAmount, Long userId);

    BigDecimal calculateDiscount(Voucher voucher, BigDecimal orderAmount);

    void useVoucher(Long voucherId);

    void releaseVoucher(Long voucherId);

    List<Voucher> getAvailableSystemVouchers();

    List<Voucher> getAvailableShopVouchers(Long shopId);

    Page<VoucherResponse> getShopVouchers(Long shopId, Pageable pageable);

    Page<VoucherResponse> getAllVouchers(Pageable pageable);

    VoucherResponse createShopVoucher(Long userId, VoucherCreateRequest request);

    VoucherResponse createPlatformVoucher(Long adminId, VoucherCreateRequest request);

    void toggleVoucherStatus(Long voucherId, Long userId, boolean isAdmin);
}
