package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.enums.DiscountType;
import com.vietspecialty.domain.enums.VoucherScope;
import com.vietspecialty.dto.request.VoucherCreateRequest;
import com.vietspecialty.dto.response.VoucherResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
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
@RequestMapping("/admin/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherController {

    private final VoucherService voucherService;

    @GetMapping
    public String listVouchers(
            @PageableDefault(size = 15, sort = "createdAt") Pageable pageable,
            Model model
    ) {
        Page<VoucherResponse> vouchers = voucherService.getAllVouchers(pageable);
        model.addAttribute("vouchers", vouchers);
        model.addAttribute("activeNav", "vouchers");
        return "admin/vouchers/index";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("activeNav", "vouchers");
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("now", LocalDateTime.now());
        return "admin/vouchers/form";
    }

    @PostMapping
    public String handleCreate(
            @CurrentUser CustomUserDetails adminDetails,
            @Valid @ModelAttribute("voucherForm") VoucherCreateRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "vouchers");
            model.addAttribute("discountTypes", DiscountType.values());
            return "admin/vouchers/form";
        }

        try {
            voucherService.createPlatformVoucher(adminDetails.getId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo voucher toàn sàn thành công!");
            return "redirect:/admin/vouchers";
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("activeNav", "vouchers");
            model.addAttribute("discountTypes", DiscountType.values());
            return "admin/vouchers/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(
            @PathVariable("id") Long voucherId,
            @CurrentUser CustomUserDetails adminDetails,
            RedirectAttributes redirectAttributes
    ) {
        voucherService.toggleVoucherStatus(voucherId, adminDetails.getId(), true);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái voucher");
        return "redirect:/admin/vouchers";
    }
}

