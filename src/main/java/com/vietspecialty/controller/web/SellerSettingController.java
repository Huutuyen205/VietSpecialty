package com.vietspecialty.controller.web;

import com.vietspecialty.dto.request.ShopUpdateRequest;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/seller/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerSettingController {

    private final ShopService shopService;
    private final CatalogService catalogService;

    @GetMapping
    public String settingsForm(@CurrentUser CustomUserDetails userDetails, Model model) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());

        model.addAttribute("shop", shop);
        model.addAttribute("provinces", catalogService.getAllProvinces());
        model.addAttribute("activeNav", "settings");

        return "seller/settings";
    }

    @PostMapping({"", "/update"})
    public String updateSettings(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "pickupAddress", required = false) String pickupAddress,
            @RequestParam(value = "addressDetail", required = false) String addressDetail,
            @RequestParam(value = "hotline", required = false) String hotline,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "logoFile", required = false) MultipartFile logoFile,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());

        String finalAddress = (pickupAddress != null && !pickupAddress.isBlank())
                ? pickupAddress
                : (addressDetail != null && !addressDetail.isBlank() ? addressDetail : shop.pickupAddress());

        String finalHotline = (hotline != null && !hotline.isBlank())
                ? hotline
                : (phone != null && !phone.isBlank() ? phone : shop.hotline());

        if (name == null || name.trim().length() < 3) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên gian hàng phải có từ 3 ký tự trở lên");
            return "redirect:/seller/settings";
        }
        if (finalAddress == null || finalAddress.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Địa chỉ lấy hàng không được để trống");
            return "redirect:/seller/settings";
        }
        if (finalHotline == null || finalHotline.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Số điện thoại hotline không được để trống");
            return "redirect:/seller/settings";
        }

        MultipartFile finalLogo = (logo != null && !logo.isEmpty()) ? logo : logoFile;

        try {
            ShopUpdateRequest updateReq = new ShopUpdateRequest(
                    name.trim(),
                    description != null ? description.trim() : shop.description(),
                    finalAddress.trim(),
                    finalHotline.trim()
            );
            shopService.updateShop(shop.id(), userDetails.getId(), updateReq, finalLogo);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin gian hàng thành công!");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/seller/settings";
    }
}
