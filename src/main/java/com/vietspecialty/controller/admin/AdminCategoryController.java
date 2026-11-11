package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.repository.CategoryRepository;
import com.vietspecialty.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public String listCategories(Model model) {
        List<Category> categories = categoryRepository.findAll();
        model.addAttribute("categories", categories);
        model.addAttribute("activeNav", "categories");
        return "admin/categories/index";
    }

    @PostMapping
    public String createCategory(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "icon", required = false) String icon,
            RedirectAttributes redirectAttributes
    ) {
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên danh mục không được để trống");
            return "redirect:/admin/categories";
        }

        String slug = SlugUtils.toSlug(name);
        Category category = Category.builder()
                .name(name.trim())
                .slug(slug)
                .description(description != null ? description.trim() : null)
                .icon(icon != null ? icon.trim() : "tag")
                .isActive(true)
                .build();

        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm danh mục đặc sản mới thành công!");
        return "redirect:/admin/categories";
    }
}

