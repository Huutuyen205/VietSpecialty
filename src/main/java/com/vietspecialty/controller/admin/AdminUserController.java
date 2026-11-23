package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.dto.response.UserResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.mapper.UserMapper;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping
    public String listUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "role", required = false) Role role,
            @RequestParam(value = "status", required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model
    ) {
        String finalKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim()
                : ((q != null && !q.isBlank()) ? q.trim()
                : ((search != null && !search.isBlank()) ? search.trim() : null));

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<UserResponse> users = userRepository.searchUsers(finalKeyword, role, status, pageable)
                .map(UserMapper::toResponse);

        model.addAttribute("users", users);
        model.addAttribute("keyword", finalKeyword);
        model.addAttribute("currentRole", role);
        model.addAttribute("currentStatus", status);
        model.addAttribute("roles", Role.values());
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("activeNav", "users");

        return "admin/users/index";
    }

    @PostMapping("/{id}/lock")
    public String lockUser(
            @PathVariable("id") Long targetUserId,
            @CurrentUser CustomUserDetails adminDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.lockUser(targetUserId, adminDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã khóa tài khoản và thu hồi toàn bộ phiên đăng nhập thành công!");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/unlock")
    public String unlockUser(
            @PathVariable("id") Long targetUserId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.unlockUser(targetUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã mở khóa tài khoản người dùng thành công!");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping({"/{id}/toggle", "/{id}/toggle-status", "/{id}/status"})
    public String toggleUserStatus(
            @PathVariable("id") Long targetUserId,
            @CurrentUser CustomUserDetails adminDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User target = userRepository.findById(targetUserId).orElse(null);
            if (target == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy người dùng");
                return "redirect:/admin/users";
            }
            if (target.getStatus() == UserStatus.ACTIVE) {
                userService.lockUser(targetUserId, adminDetails.getId());
                redirectAttributes.addFlashAttribute("successMessage", "Đã khóa tài khoản và thu hồi phiên đăng nhập!");
            } else {
                userService.unlockUser(targetUserId);
                redirectAttributes.addFlashAttribute("successMessage", "Đã mở khóa tài khoản người dùng!");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }
}
