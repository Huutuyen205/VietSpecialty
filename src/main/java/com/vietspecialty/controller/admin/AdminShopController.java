package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/shops")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShopController {

    private final ShopService shopService;

    @GetMapping
    public String listShops(
            @RequestParam(name = "status", required = false) ShopStatus status,
            @PageableDefault(size = 15, sort = "createdAt") Pageable pageable,
            Model model
    ) {
        Page<ShopResponse> shops;
        if (status != null) {
            shops = shopService.getShopsByStatus(status, pageable);
        } else {
            shops = shopService.getAllShops(pageable);
        }

        model.addAttribute("shops", shops);
        model.addAttribute("currentStatus", status);
        model.addAttribute("activeNav", "shops");
        return "admin/shops/index";
    }

    @PostMapping("/{id}/approve")
    public String approveShop(
            @PathVariable("id") Long shopId,
            @CurrentUser CustomUserDetails adminDetails,
            RedirectAttributes redirectAttributes
    ) {
        shopService.approveShop(shopId, adminDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt gian hàng thành công!");
        return "redirect:/admin/shops";
    }

    @PostMapping("/{id}/suspend")
    public String suspendShop(
            @PathVariable("id") Long shopId,
            @CurrentUser CustomUserDetails adminDetails,
            RedirectAttributes redirectAttributes
    ) {
        shopService.suspendShop(shopId, adminDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Đã tạm dừng hoạt động gian hàng!");
        return "redirect:/admin/shops";
    }
}

