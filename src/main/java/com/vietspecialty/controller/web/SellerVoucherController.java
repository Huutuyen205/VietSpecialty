package com.vietspecialty.controller.web;

import com.vietspecialty.domain.enums.DiscountType;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.request.VoucherCreateRequest;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.dto.response.VoucherResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.ShopService;
import com.vietspecialty.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Slf4j
@Controller
@RequestMapping("/seller/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerVoucherController {

    private final VoucherService voucherService;
    private final ShopService shopService;

    @GetMapping
    public String index(
            @CurrentUser CustomUserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable,
            Model model
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        if (shop.status() != ShopStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_APPROVED);
        }

        Page<VoucherResponse> vouchers = voucherService.getShopVouchers(shop.id(), pageable);

        model.addAttribute("shop", shop);
        model.addAttribute("vouchers", vouchers);
        model.addAttribute("activeNav", "vouchers");
        return "seller/vouchers/index";
    }

    @GetMapping("/create")
    public String createForm(@CurrentUser CustomUserDetails userDetails, Model model) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        if (shop.status() != ShopStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_APPROVED);
        }

        model.addAttribute("shop", shop);
        model.addAttribute("activeNav", "vouchers");
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("now", LocalDateTime.now());
        return "seller/vouchers/form";
    }

    @PostMapping
    public String handleCreate(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @ModelAttribute("voucherForm") VoucherCreateRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());

        if (bindingResult.hasErrors()) {
            model.addAttribute("shop", shop);
            model.addAttribute("activeNav", "vouchers");
            model.addAttribute("discountTypes", DiscountType.values());
            return "seller/vouchers/form";
        }

        try {
            voucherService.createShopVoucher(userDetails.getId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo mã giảm giá thành công!");
            return "redirect:/seller/vouchers";
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("shop", shop);
            model.addAttribute("activeNav", "vouchers");
            model.addAttribute("discountTypes", DiscountType.values());
            return "seller/vouchers/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(
            @PathVariable("id") Long voucherId,
            @CurrentUser CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        voucherService.toggleVoucherStatus(voucherId, userDetails.getId(), false);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái voucher");
        return "redirect:/seller/vouchers";
    }
}

