package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.entity.Voucher;
import com.vietspecialty.domain.enums.DiscountType;
import com.vietspecialty.domain.enums.VoucherScope;
import com.vietspecialty.dto.request.VoucherCreateRequest;
import com.vietspecialty.dto.response.VoucherResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.mapper.VoucherMapper;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.repository.VoucherRepository;
import com.vietspecialty.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    @Override
    public Voucher validateAndGetVoucher(String code, Long shopId, BigDecimal orderAmount, Long userId) {
        if (code == null || code.isBlank()) {
            return null;
        }

        Voucher voucher = voucherRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));

        if (!Boolean.TRUE.equals(voucher.getIsActive())) {
            throw new BusinessException(ErrorCode.VOUCHER_EXPIRED, "Mã giảm giá hiện đang bị vô hiệu hóa");
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartAt() != null && now.isBefore(voucher.getStartAt())) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_STARTED);
        }

        if (voucher.getEndAt() != null && now.isAfter(voucher.getEndAt())) {
            throw new BusinessException(ErrorCode.VOUCHER_EXPIRED);
        }

        if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new BusinessException(ErrorCode.VOUCHER_USAGE_EXCEEDED);
        }

        if (voucher.getMinOrderValue() != null && orderAmount.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new BusinessException(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);
        }

        if (voucher.getScope() == VoucherScope.SHOP) {
            if (voucher.getShop() == null || shopId == null || !voucher.getShop().getId().equals(shopId)) {
                throw new BusinessException(ErrorCode.VOUCHER_SHOP_MISMATCH);
            }
        }

        return voucher;
    }

    @Override
    public BigDecimal calculateDiscount(Voucher voucher, BigDecimal orderAmount) {
        if (voucher == null || orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;
        if (voucher.getDiscountType() == DiscountType.PERCENT) {
            BigDecimal percentage = voucher.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            discount = orderAmount.multiply(percentage).setScale(0, RoundingMode.HALF_UP);

            if (voucher.getMaxDiscount() != null && discount.compareTo(voucher.getMaxDiscount()) > 0) {
                discount = voucher.getMaxDiscount();
            }
        } else {
            discount = voucher.getDiscountValue();
        }

        // Không bao giờ giảm vượt quá giá trị đơn hàng
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        return discount;
    }

    @Override
    @Transactional
    public void useVoucher(Long voucherId) {
        if (voucherId == null) return;
        int updated = voucherRepository.incrementUsedCountAtomic(voucherId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.VOUCHER_USAGE_EXCEEDED);
        }
    }

    @Override
    @Transactional
    public void releaseVoucher(Long voucherId) {
        if (voucherId == null) return;
        voucherRepository.decrementUsedCountAtomic(voucherId);
    }

    @Override
    public List<Voucher> getAvailableSystemVouchers() {
        return voucherRepository.findByScopeAndIsActiveTrue(VoucherScope.PLATFORM);
    }

    @Override
    public List<Voucher> getAvailableShopVouchers(Long shopId) {
        if (shopId == null) return List.of();
        return voucherRepository.findByShopIdAndIsActiveTrue(shopId);
    }

    @Override
    public Page<VoucherResponse> getShopVouchers(Long shopId, Pageable pageable) {
        return voucherRepository.findByShopId(shopId, pageable)
                .map(VoucherMapper::toResponse);
    }

    @Override
    public Page<VoucherResponse> getAllVouchers(Pageable pageable) {
        return voucherRepository.findAll(pageable)
                .map(VoucherMapper::toResponse);
    }

    @Override
    @Transactional
    public VoucherResponse createShopVoucher(Long userId, VoucherCreateRequest request) {
        Shop shop = shopRepository.findByOwnerId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String code = request.code().trim().toUpperCase();
        if (voucherRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã giảm giá '" + code + "' đã tồn tại trên hệ thống");
        }

        if (request.endAt().isBefore(request.startAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        Voucher voucher = Voucher.builder()
                .code(code)
                .name(request.name().trim())
                .scope(VoucherScope.SHOP)
                .shop(shop)
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .maxDiscount(request.maxDiscount())
                .minOrderValue(request.minOrderValue() != null ? request.minOrderValue() : BigDecimal.ZERO)
                .usageLimit(request.usageLimit())
                .usedCount(0)
                .perUserLimit(request.perUserLimit() != null ? request.perUserLimit() : 1)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .isActive(true)
                .createdBy(user)
                .build();

        Voucher saved = voucherRepository.save(voucher);
        log.info("Gian hàng {} tạo voucher thành công: {}", shop.getName(), code);
        return VoucherMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VoucherResponse createPlatformVoucher(Long adminId, VoucherCreateRequest request) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String code = request.code().trim().toUpperCase();
        if (voucherRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã giảm giá '" + code + "' đã tồn tại trên hệ thống");
        }

        if (request.endAt().isBefore(request.startAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        Voucher voucher = Voucher.builder()
                .code(code)
                .name(request.name().trim())
                .scope(VoucherScope.PLATFORM)
                .shop(null)
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .maxDiscount(request.maxDiscount())
                .minOrderValue(request.minOrderValue() != null ? request.minOrderValue() : BigDecimal.ZERO)
                .usageLimit(request.usageLimit())
                .usedCount(0)
                .perUserLimit(request.perUserLimit() != null ? request.perUserLimit() : 1)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .isActive(true)
                .createdBy(admin)
                .build();

        Voucher saved = voucherRepository.save(voucher);
        log.info("Admin {} tạo voucher toàn sàn thành công: {}", admin.getEmail(), code);
        return VoucherMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void toggleVoucherStatus(Long voucherId, Long userId, boolean isAdmin) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));

        if (!isAdmin) {
            Shop shop = shopRepository.findByOwnerId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
            if (voucher.getShop() == null || !voucher.getShop().getId().equals(shop.getId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền quản lý voucher này");
            }
        }

        voucher.setIsActive(!Boolean.TRUE.equals(voucher.getIsActive()));
        voucherRepository.save(voucher);
        log.info("Voucher {} trạng thái isActive cập nhật thành: {}", voucher.getCode(), voucher.getIsActive());
    }
}
