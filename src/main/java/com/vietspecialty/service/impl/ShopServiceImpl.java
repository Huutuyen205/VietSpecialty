package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.request.ShopRegisterRequest;
import com.vietspecialty.dto.request.ShopUpdateRequest;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.mapper.ShopMapper;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.security.SessionInvalidator;
import com.vietspecialty.service.ShopService;
import com.vietspecialty.service.StorageService;
import com.vietspecialty.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ProvinceRepository provinceRepository;
    private final StorageService storageService;
    private final SessionInvalidator sessionInvalidator;

    @Override
    public ShopResponse getShopBySlug(String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND, "Không tìm thấy gian hàng"));

        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_APPROVED, "Gian hàng hiện đang tạm dừng hoặc chưa được duyệt");
        }

        return ShopMapper.toResponse(shop);
    }

    @Override
    public ShopResponse getShopById(Long id) {
        Shop shop = getShopEntityById(id);
        return ShopMapper.toResponse(shop);
    }

    @Override
    public ShopResponse getShopByOwnerId(Long ownerId) {
        Shop shop = shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND, "Người dùng chưa tạo gian hàng"));
        return ShopMapper.toResponse(shop);
    }

    @Override
    public Shop getShopEntityById(Long id) {
        return shopRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
    }

    @Override
    @Transactional
    public ShopResponse registerShop(Long userId, ShopRegisterRequest request, MultipartFile logoFile) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String shopName = request.name().trim();
        Province province = provinceRepository.findById(request.provinceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Tỉnh/thành phố không hợp lệ"));

        // Nếu người dùng đã có hồ sơ bị REJECTED -> Cho phép nộp lại hồ sơ
        var existingShopOpt = shopRepository.findByOwnerId(userId);
        if (existingShopOpt.isPresent()) {
            Shop existingShop = existingShopOpt.get();
            if (existingShop.getStatus() != ShopStatus.REJECTED) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mỗi tài khoản chỉ được phép sở hữu một gian hàng");
            }
            if (!existingShop.getName().equalsIgnoreCase(shopName) && shopRepository.existsByNameIgnoreCase(shopName)) {
                throw new BusinessException(ErrorCode.SHOP_NAME_EXISTS);
            }
            if (logoFile != null && !logoFile.isEmpty()) {
                existingShop.setLogoUrl(storageService.uploadImage(logoFile, "shops"));
            }
            existingShop.setName(shopName);
            existingShop.setDescription(request.description() != null ? request.description().trim() : null);
            existingShop.setAddressDetail(request.pickupAddress().trim());
            existingShop.setPhone(request.hotline().trim());
            existingShop.setProvince(province);
            existingShop.setStatus(ShopStatus.PENDING);
            existingShop.setRejectReason(null);
            Shop reSubmitted = shopRepository.save(existingShop);
            log.info("Rejected shop re-submitted for approval: id={}, name={}, ownerId={}", reSubmitted.getId(), reSubmitted.getName(), userId);
            return ShopMapper.toResponse(reSubmitted);
        }

        if (shopRepository.existsByNameIgnoreCase(shopName)) {
            throw new BusinessException(ErrorCode.SHOP_NAME_EXISTS);
        }

        // Upload logo nếu có
        String logoUrl = null;
        if (logoFile != null && !logoFile.isEmpty()) {
            logoUrl = storageService.uploadImage(logoFile, "shops");
        }

        // Tạo slug duy nhất
        String baseSlug = SlugUtils.toSlug(shopName);
        String slug = baseSlug;
        int counter = 1;
        while (shopRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + counter++;
        }

        Shop shop = Shop.builder()
                .owner(owner)
                .name(shopName)
                .slug(slug)
                .description(request.description() != null ? request.description().trim() : null)
                .logoUrl(logoUrl)
                .addressDetail(request.pickupAddress().trim())
                .phone(request.hotline().trim())
                .province(province)
                .status(ShopStatus.PENDING)
                .build();

        Shop savedShop = shopRepository.save(shop);
        log.info("New shop registered and pending approval: id={}, name={}, ownerId={}", savedShop.getId(), savedShop.getName(), userId);

        return ShopMapper.toResponse(savedShop);
    }

    @Override
    @Transactional
    public ShopResponse approveShop(Long shopId, Long adminId) {
        Shop shop = getShopEntityById(shopId);
        User admin = userRepository.findById(adminId).orElse(null);

        if (shop.getStatus() == ShopStatus.APPROVED) {
            return ShopMapper.toResponse(shop);
        }

        shop.setStatus(ShopStatus.APPROVED);
        shop.setRejectReason(null);
        shop.setApprovedBy(admin);
        shop.setApprovedAt(LocalDateTime.now());

        User owner = shop.getOwner();
        if (owner != null && owner.getRole() == Role.USER) {
            owner.setRole(Role.SELLER);
            userRepository.save(owner);
            sessionInvalidator.invalidateUserSessions(owner.getId());
            log.info("Upgraded user {} to SELLER after shop approval", owner.getEmail());
        }

        Shop savedShop = shopRepository.save(shop);
        log.info("Shop approved: id={}, approvedByAdminId={}", shopId, adminId);
        return ShopMapper.toResponse(savedShop);
    }

    @Override
    @Transactional
    public ShopResponse rejectShop(Long shopId, Long adminId, String reason) {
        Shop shop = getShopEntityById(shopId);
        shop.setStatus(ShopStatus.REJECTED);
        shop.setRejectReason(reason);
        Shop savedShop = shopRepository.save(shop);
        log.info("Shop rejected: id={}, reason={}, adminId={}", shopId, reason, adminId);
        return ShopMapper.toResponse(savedShop);
    }

    @Override
    @Transactional
    public ShopResponse suspendShop(Long shopId, Long adminId) {
        Shop shop = getShopEntityById(shopId);
        shop.setStatus(ShopStatus.SUSPENDED);
        Shop savedShop = shopRepository.save(shop);
        log.info("Shop suspended: id={}, adminId={}", shopId, adminId);
        return ShopMapper.toResponse(savedShop);
    }

    @Override
    public Page<ShopResponse> getAllShops(Pageable pageable) {
        return shopRepository.findAll(pageable)
                .map(ShopMapper::toResponse);
    }

    @Override
    @Transactional
    public ShopResponse updateShop(Long shopId, Long ownerId, ShopUpdateRequest request, MultipartFile logoFile) {
        Shop shop = getShopEntityById(shopId);
        if (!shop.getOwner().getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa gian hàng này");
        }

        String newName = request.name().trim();
        if (!shop.getName().equalsIgnoreCase(newName) && shopRepository.existsByNameIgnoreCase(newName)) {
            throw new BusinessException(ErrorCode.SHOP_NAME_EXISTS);
        }

        shop.setName(newName);
        shop.setDescription(request.description() != null ? request.description().trim() : null);
        shop.setAddressDetail(request.pickupAddress().trim());
        shop.setPhone(request.hotline().trim());

        if (logoFile != null && !logoFile.isEmpty()) {
            String newLogoUrl = storageService.uploadImage(logoFile, "shops");
            shop.setLogoUrl(newLogoUrl);
        }

        Shop updated = shopRepository.save(shop);
        return ShopMapper.toResponse(updated);
    }

    @Override
    public Page<ShopResponse> getShopsByStatus(ShopStatus status, Pageable pageable) {
        return shopRepository.findByStatus(status, pageable).map(ShopMapper::toResponse);
    }

    @Override
    public boolean existsByOwnerId(Long ownerId) {
        return shopRepository.existsByOwnerId(ownerId);
    }
}
