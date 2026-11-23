package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Order;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.dto.request.AddressRequest;
import com.vietspecialty.dto.request.ChangePasswordRequest;
import com.vietspecialty.dto.request.ProfileUpdateRequest;
import com.vietspecialty.dto.response.AddressResponse;
import com.vietspecialty.dto.response.OrderDetailResponse;
import com.vietspecialty.dto.response.OrderSummaryResponse;
import com.vietspecialty.dto.response.UserResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.security.SecurityUtils;
import com.vietspecialty.service.AddressService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final AddressService addressService;
    private final ProvinceRepository provinceRepository;
    private final OrderService orderService;

    @GetMapping("/orders")
    public String accountOrders(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/account/orders";
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<OrderSummaryResponse> orderPage = orderService.getBuyerOrders(userDetails.getId(), status, pageable);
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("selectedStatus", status != null ? status.name() : "ALL");
        model.addAttribute("statuses", OrderStatus.values());
        return "web/orders/index";
    }

    @GetMapping("/orders/{code}")
    public String accountOrderDetail(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable String code,
            Model model
    ) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/account/orders/" + code;
        }
        OrderDetailResponse detail = orderService.getOrderDetail(code, userDetails.getId());
        model.addAttribute("order", detail);
        return "web/orders/detail";
    }

    @GetMapping("/profile")
    public String profilePage(Model model) {
        Long userId = SecurityUtils.getUserId().orElseThrow();
        UserResponse user = userService.getProfile(userId);
        model.addAttribute("user", user);
        model.addAttribute("profileRequest", new ProfileUpdateRequest(user.fullName(), user.phone()));
        return "account/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(
            @Valid @ModelAttribute("profileRequest") ProfileUpdateRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        Long userId = SecurityUtils.getUserId().orElseThrow();
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", userService.getProfile(userId));
            return "account/profile";
        }

        try {
            userService.updateProfile(userId, request);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin cá nhân thành công");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/account/profile";
    }

    @GetMapping("/password")
    public String passwordPage(Model model) {
        model.addAttribute("passwordRequest", new ChangePasswordRequest("", "", ""));
        return "account/password";
    }

    @PostMapping("/password")
    public String changePassword(
            @Valid @ModelAttribute("passwordRequest") ChangePasswordRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (!request.isPasswordMatching()) {
            bindingResult.rejectValue("confirmNewPassword", "error.confirmNewPassword", "Mật khẩu xác nhận không khớp");
        }

        if (bindingResult.hasErrors()) {
            return "account/password";
        }

        Long userId = SecurityUtils.getUserId().orElseThrow();
        try {
            userService.changePassword(userId, request);
            redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công");
            return "redirect:/account/password";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/account/password";
        }
    }

    @GetMapping("/addresses")
    public String addressesPage(Model model) {
        Long userId = SecurityUtils.getUserId().orElseThrow();
        List<AddressResponse> addresses = addressService.getUserAddresses(userId);
        model.addAttribute("addresses", addresses);
        model.addAttribute("provinces", provinceRepository.findAllByOrderByNameAsc());
        if (!model.containsAttribute("addressRequest")) {
            model.addAttribute("addressRequest", new AddressRequest("", "", null, "", "", false));
        }
        return "account/addresses";
    }

    @PostMapping("/addresses")
    public String addAddress(
            @Valid @ModelAttribute("addressRequest") AddressRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        Long userId = SecurityUtils.getUserId().orElseThrow();
        if (bindingResult.hasErrors()) {
            model.addAttribute("addresses", addressService.getUserAddresses(userId));
            model.addAttribute("provinces", provinceRepository.findAllByOrderByNameAsc());
            return "account/addresses";
        }

        try {
            addressService.createAddress(userId, request);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm địa chỉ giao hàng thành công");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/account/addresses";
    }

    @PostMapping("/addresses/{id}/default")
    public String setDefaultAddress(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Long userId = SecurityUtils.getUserId().orElseThrow();
        try {
            addressService.setDefaultAddress(id, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đặt làm địa chỉ giao hàng mặc định");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/account/addresses";
    }

    @PostMapping("/addresses/{id}/delete")
    public String deleteAddress(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Long userId = SecurityUtils.getUserId().orElseThrow();
        try {
            addressService.deleteAddress(id, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa địa chỉ giao hàng");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/account/addresses";
    }
}
