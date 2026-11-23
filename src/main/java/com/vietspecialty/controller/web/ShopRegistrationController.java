package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.request.ShopRegisterRequest;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ShopService;
import com.vietspecialty.service.StorageService;
import com.vietspecialty.util.SlugUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ShopRegistrationController {

    private final ShopService shopService;
    private final CatalogService catalogService;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ProvinceRepository provinceRepository;
    private final StorageService storageService;

    @GetMapping("/account/shop-registration")
    public String shopRegistrationPage(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "false") boolean reapply,
            Model model
    ) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/account/shop-registration";
        }

        if (userDetails.getRole() == Role.ADMIN) {
            return "redirect:/admin/shops";
        }

        if (userDetails.getRole() == Role.SELLER) {
            Optional<Shop> existingOpt = shopRepository.findByOwnerId(userDetails.getId());
            if (existingOpt.isPresent() && existingOpt.get().getStatus() == ShopStatus.APPROVED) {
                return "redirect:/seller/dashboard";
            }
        }

        if (shopService.existsByOwnerId(userDetails.getId())) {
            Shop shopEntity = shopRepository.findByOwnerId(userDetails.getId()).orElse(null);
            if (shopEntity != null) {
                if (shopEntity.getStatus() == ShopStatus.REJECTED && reapply) {
                    Integer provinceId = shopEntity.getProvince() != null ? shopEntity.getProvince().getId() : null;
                    model.addAttribute("shopRequest", new ShopRegisterRequest(
                            shopEntity.getName(),
                            shopEntity.getDescription() != null ? shopEntity.getDescription() : "",
                            shopEntity.getAddressDetail() != null ? shopEntity.getAddressDetail() : "",
                            shopEntity.getPhone() != null ? shopEntity.getPhone() : "",
                            provinceId
                    ));
                    model.addAttribute("provinces", catalogService.getAllProvinces());
                    model.addAttribute("isReapply", true);
                    model.addAttribute("rejectReason", shopEntity.getRejectReason());
                    return "account/shop-registration";
                }

                ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
                model.addAttribute("shop", shop);
                model.addAttribute("rejectReason", shopEntity.getRejectReason());
                return "account/shop-registration-status";
            }
        }

        model.addAttribute("shopRequest", new ShopRegisterRequest("", "", "", "", null));
        model.addAttribute("provinces", catalogService.getAllProvinces());
        return "account/shop-registration";
    }

    @PostMapping("/account/shop-registration")
    @Transactional
    public String handleShopRegistration(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @ModelAttribute("shopRequest") ShopRegisterRequest request,
            BindingResult bindingResult,
            @RequestParam(value = "logo", required = false) MultipartFile logoFile,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/account/shop-registration";
        }

        if (userDetails.getRole() == Role.ADMIN) {
            return "redirect:/admin/shops";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("provinces", catalogService.getAllProvinces());
            return "account/shop-registration";
        }

        try {
            Optional<Shop> existingOpt = shopRepository.findByOwnerId(userDetails.getId());
            if (existingOpt.isPresent()) {
                Shop existingShop = existingOpt.get();
                if (existingShop.getStatus() == ShopStatus.REJECTED) {
                    resubmitRejectedShop(existingShop, request, logoFile);
                    redirectAttributes.addFlashAttribute("successMessage", "Hồ sơ đăng ký gian hàng đã được cập nhật và gửi lại để chờ xét duyệt!");
                    return "redirect:/account/shop-registration";
                }
            }

            shopService.registerShop(userDetails.getId(), request, logoFile);
            redirectAttributes.addFlashAttribute("successMessage", "Hồ sơ đăng ký gian hàng đã được gửi thành công và đang chờ xét duyệt!");
            return "redirect:/account/shop-registration";
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("provinces", catalogService.getAllProvinces());
            return "account/shop-registration";
        }
    }

    private void resubmitRejectedShop(Shop shop, ShopRegisterRequest request, MultipartFile logoFile) {
        String shopName = request.name().trim();
        if (!shop.getName().equalsIgnoreCase(shopName) && shopRepository.existsByNameIgnoreCase(shopName)) {
            throw new BusinessException(com.vietspecialty.exception.ErrorCode.SHOP_NAME_EXISTS);
        }

        Province province = provinceRepository.findById(request.provinceId())
                .orElseThrow(() -> new BusinessException(com.vietspecialty.exception.ErrorCode.VALIDATION_ERROR, "Tỉnh/thành phố không hợp lệ"));

        if (logoFile != null && !logoFile.isEmpty()) {
            String logoUrl = storageService.uploadImage(logoFile, "shops");
            shop.setLogoUrl(logoUrl);
        }

        if (!shop.getName().equalsIgnoreCase(shopName)) {
            String baseSlug = SlugUtils.toSlug(shopName);
            String slug = baseSlug;
            int counter = 1;
            while (shopRepository.findBySlug(slug).isPresent()) {
                slug = baseSlug + "-" + counter++;
            }
            shop.setSlug(slug);
        }

        shop.setName(shopName);
        shop.setDescription(request.description() != null ? request.description().trim() : null);
        shop.setAddressDetail(request.pickupAddress().trim());
        shop.setPhone(request.hotline().trim());
        shop.setProvince(province);
        shop.setStatus(ShopStatus.PENDING);
        shop.setRejectReason(null);
        shopRepository.save(shop);
    }
}
