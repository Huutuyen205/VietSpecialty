package com.vietspecialty.controller.web;

import com.vietspecialty.dto.request.RegisterRequest;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.SecurityUtils;
import com.vietspecialty.service.AuthService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "registered", required = false) String registered,
            Model model
    ) {
        if (SecurityUtils.isAuthenticated()) {
            return "redirect:/";
        }

        if (error != null) {
            model.addAttribute("errorMessage", error);
        }
        if (email != null && !email.isBlank()) {
            model.addAttribute("email", email.trim());
        }
        if (logout != null) {
            model.addAttribute("successMessage", "Bạn đã đăng xuất thành công");
        }
        if (registered != null) {
            model.addAttribute("successMessage", "Đăng ký thành công! Vui lòng đăng nhập");
        }

        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (SecurityUtils.isAuthenticated()) {
            return "redirect:/";
        }
        model.addAttribute("registerRequest", new RegisterRequest("", "", "", "", ""));
        return "auth/register";
    }

    @PostMapping("/register")
    public String handleRegister(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (!request.isPasswordMatching()) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", "Mật khẩu xác nhận không trùng khớp");
        }

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            authService.register(request);
            redirectAttributes.addFlashAttribute("successMessage", "Đăng ký thành công! Vui lòng đăng nhập.");
            return "redirect:/login?registered=true";
        } catch (BusinessException ex) {
            bindingResult.rejectValue("email", "error.email", ex.getMessage());
            return "auth/register";
        }
    }
}
