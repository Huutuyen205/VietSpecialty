package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.repository.CategoryRepository;
import com.vietspecialty.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping({"", "/create"})
    public String listCategories(Model model) {
        List<Category> categories = categoryRepository.findAllByOrderBySortOrderAsc();
        model.addAttribute("categories", categories);
        model.addAttribute("activeNav", "categories");
        return "admin/categories/index";
    }

    @PostMapping({"", "/create"})
    @CacheEvict(value = "categories", allEntries = true)
    public String createCategory(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "icon", required = false) String icon,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "0") Integer sortOrder,
            RedirectAttributes redirectAttributes
    ) {
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên danh mục không được để trống");
            return "redirect:/admin/categories";
        }

        String trimmedName = name.trim();
        String slug = SlugUtils.toSlug(trimmedName);

        boolean duplicate = categoryRepository.findBySlug(slug).isPresent()
                || categoryRepository.findAll().stream().anyMatch(c -> c.getName().equalsIgnoreCase(trimmedName));
        if (duplicate) {
            redirectAttributes.addFlashAttribute("errorMessage", "Danh mục đặc sản '" + trimmedName + "' đã tồn tại trên hệ thống!");
            return "redirect:/admin/categories";
        }

        Category category = Category.builder()
                .name(trimmedName)
                .slug(slug)
                .description(description != null ? description.trim() : null)
                .icon(icon != null && !icon.isBlank() ? icon.trim() : "tag")
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .isActive(true)
                .build();

        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm danh mục đặc sản mới thành công!");
        return "redirect:/admin/categories";
    }

    @PostMapping({"/{id}/edit", "/{id}/update", "/{id}"})
    @CacheEvict(value = "categories", allEntries = true)
    public String updateCategory(
            @PathVariable("id") Integer id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "icon", required = false) String icon,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Category> catOpt = categoryRepository.findById(id);
        if (catOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy danh mục cần chỉnh sửa");
            return "redirect:/admin/categories";
        }
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên danh mục không được để trống");
            return "redirect:/admin/categories";
        }

        Category category = catOpt.get();
        String trimmedName = name.trim();
        String newSlug = SlugUtils.toSlug(trimmedName);

        Optional<Category> existingBySlug = categoryRepository.findBySlug(newSlug);
        if (existingBySlug.isPresent() && !existingBySlug.get().getId().equals(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên danh mục bị trùng với danh mục khác!");
            return "redirect:/admin/categories";
        }

        category.setName(trimmedName);
        category.setSlug(newSlug);
        category.setDescription(description != null ? description.trim() : null);
        if (icon != null && !icon.isBlank()) {
            category.setIcon(icon.trim());
        }
        if (sortOrder != null) {
            category.setSortOrder(sortOrder);
        }

        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật danh mục đặc sản thành công!");
        return "redirect:/admin/categories";
    }

    @PostMapping({"/{id}/toggle", "/{id}/status"})
    @CacheEvict(value = "categories", allEntries = true)
    public String toggleCategory(
            @PathVariable("id") Integer id,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Category> catOpt = categoryRepository.findById(id);
        if (catOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy danh mục");
            return "redirect:/admin/categories";
        }

        Category category = catOpt.get();
        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage",
                Boolean.TRUE.equals(category.getIsActive())
                        ? "Đã bật hiển thị danh mục '" + category.getName() + "'"
                        : "Đã tạm ẩn danh mục '" + category.getName() + "'");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/delete")
    @CacheEvict(value = "categories", allEntries = true)
    public String deleteCategory(
            @PathVariable("id") Integer id,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Category> catOpt = categoryRepository.findById(id);
        if (catOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy danh mục cần xóa");
            return "redirect:/admin/categories";
        }

        Category category = catOpt.get();
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa danh mục '" + category.getName() + "' thành công!");
        } catch (DataIntegrityViolationException ex) {
            category.setIsActive(false);
            categoryRepository.save(category);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Danh mục '" + category.getName() + "' đang chứa sản phẩm nên đã được chuyển sang trạng thái Tạm ẩn.");
        }
        return "redirect:/admin/categories";
    }
}


