package com.vietspecialty.controller.web;

import com.vietspecialty.dto.request.ShopRegisterRequest;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ShopRegistrationController {

    private final ShopService shopService;
    private final CatalogService catalogService;

    @GetMapping("/account/shop-registration")
    public String shopRegistrationPage(@CurrentUser CustomUserDetails userDetails, Model model) {
        if (shopService.existsByOwnerId(userDetails.getId())) {
            ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
            model.addAttribute("shop", shop);
            return "account/shop-registration-status";
        }

        model.addAttribute("shopRequest", new ShopRegisterRequest("", "", "", "", null));
        model.addAttribute("provinces", catalogService.getAllProvinces());
        return "account/shop-registration";
    }

    @PostMapping("/account/shop-registration")
    public String handleShopRegistration(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @ModelAttribute("shopRequest") ShopRegisterRequest request,
            BindingResult bindingResult,
            @RequestParam(value = "logo", required = false) MultipartFile logoFile,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("provinces", catalogService.getAllProvinces());
            return "account/shop-registration";
        }

        try {
            shopService.registerShop(userDetails.getId(), request, logoFile);
            redirectAttributes.addFlashAttribute("successMessage", "Hồ sơ đăng ký gian hàng đã được gửi thành công và đang chờ xét duyệt!");
            return "redirect:/account/shop-registration";
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("provinces", catalogService.getAllProvinces());
            return "account/shop-registration";
        }
    }
}

