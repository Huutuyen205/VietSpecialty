package com.vietspecialty.service;

import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.request.ShopRegisterRequest;
import com.vietspecialty.dto.request.ShopUpdateRequest;
import com.vietspecialty.dto.response.ShopResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ShopService {

    ShopResponse getShopBySlug(String slug);

    ShopResponse getShopById(Long id);

    ShopResponse getShopByOwnerId(Long ownerId);

    Shop getShopEntityById(Long id);

    ShopResponse registerShop(Long userId, ShopRegisterRequest request, MultipartFile logoFile);

    ShopResponse approveShop(Long shopId, Long adminId);

    ShopResponse rejectShop(Long shopId, Long adminId, String reason);

    ShopResponse updateShop(Long shopId, Long ownerId, ShopUpdateRequest request, MultipartFile logoFile);

    Page<ShopResponse> getShopsByStatus(ShopStatus status, Pageable pageable);

    Page<ShopResponse> getAllShops(Pageable pageable);

    ShopResponse suspendShop(Long shopId, Long adminId);

    boolean existsByOwnerId(Long ownerId);
}
