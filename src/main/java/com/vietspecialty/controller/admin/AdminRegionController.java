package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Region;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.RegionRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/admin/regions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRegionController {

    private final RegionRepository regionRepository;
    private final ProvinceRepository provinceRepository;

    @GetMapping
    public String listRegionsAndProvinces(
            @RequestParam(value = "regionId", required = false) Integer regionId,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model
    ) {
        List<Region> regions = regionRepository.findAllByOrderBySortOrderAsc();
        List<Province> provinces = (regionId != null)
                ? provinceRepository.findByRegionIdOrderByNameAsc(regionId)
                : provinceRepository.findAllByOrderByNameAsc();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            provinces = provinces.stream()
                    .filter(p -> p.getName().toLowerCase().contains(lower) || p.getSlug().toLowerCase().contains(lower))
                    .toList();
        }

        Map<Integer, Integer> provinceCountByRegion = new LinkedHashMap<>();
        for (Region r : regions) {
            provinceCountByRegion.put(r.getId(), provinceRepository.findByRegionIdOrderByNameAsc(r.getId()).size());
        }

        model.addAttribute("regions", regions);
        model.addAttribute("provinces", provinces);
        model.addAttribute("totalProvincesCount", provinceRepository.count());
        model.addAttribute("provinceCountByRegion", provinceCountByRegion);
        model.addAttribute("currentRegionId", regionId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("activeNav", "regions");

        return "admin/regions/index";
    }

    @PostMapping({"/{id}/update", "/{id}/edit", "/{id}"})
    @CacheEvict(value = "regions", allEntries = true)
    public String updateRegion(
            @PathVariable("id") Integer id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "colorHex", required = false) String colorHex,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Region> regionOpt = regionRepository.findById(id);
        if (regionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy vùng miền cần cập nhật");
            return "redirect:/admin/regions";
        }
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên vùng miền không được để trống");
            return "redirect:/admin/regions";
        }

        Region region = regionOpt.get();
        region.setName(name.trim());
        if (description != null) {
            region.setDescription(description.trim());
        }
        if (colorHex != null && !colorHex.isBlank()) {
            region.setColorHex(colorHex.trim());
        }
        if (sortOrder != null) {
            region.setSortOrder(sortOrder);
        }

        regionRepository.save(region);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật thông tin vùng miền '" + region.getName() + "' thành công!");
        return "redirect:/admin/regions";
    }

    @PostMapping({"/provinces", "/provinces/create"})
    public String createProvince(
            @RequestParam("name") String name,
            @RequestParam("regionId") Integer regionId,
            RedirectAttributes redirectAttributes
    ) {
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên tỉnh/thành phố không được để trống");
            return "redirect:/admin/regions";
        }

        Optional<Region> regionOpt = regionRepository.findById(regionId);
        if (regionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vùng miền được chọn không hợp lệ");
            return "redirect:/admin/regions";
        }

        String trimmedName = name.trim();
        String slug = SlugUtils.toSlug(trimmedName);
        if (provinceRepository.findBySlug(slug).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tỉnh/thành phố '" + trimmedName + "' đã tồn tại!");
            return "redirect:/admin/regions";
        }

        Province province = Province.builder()
                .name(trimmedName)
                .slug(slug)
                .region(regionOpt.get())
                .build();

        provinceRepository.save(province);
        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm tỉnh/thành phố '" + trimmedName + "' thành công!");
        return "redirect:/admin/regions";
    }

    @PostMapping({"/provinces/{id}/update", "/provinces/{id}/edit"})
    public String updateProvince(
            @PathVariable("id") Integer id,
            @RequestParam("name") String name,
            @RequestParam("regionId") Integer regionId,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Province> provOpt = provinceRepository.findById(id);
        if (provOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tỉnh/thành phố");
            return "redirect:/admin/regions";
        }

        Optional<Region> regionOpt = regionRepository.findById(regionId);
        if (regionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vùng miền không hợp lệ");
            return "redirect:/admin/regions";
        }

        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên tỉnh/thành phố không được để trống");
            return "redirect:/admin/regions";
        }

        Province province = provOpt.get();
        String trimmedName = name.trim();
        String newSlug = SlugUtils.toSlug(trimmedName);

        Optional<Province> existingBySlug = provinceRepository.findBySlug(newSlug);
        if (existingBySlug.isPresent() && !existingBySlug.get().getId().equals(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên tỉnh/thành phố bị trùng!");
            return "redirect:/admin/regions";
        }

        province.setName(trimmedName);
        province.setSlug(newSlug);
        province.setRegion(regionOpt.get());
        provinceRepository.save(province);

        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật tỉnh/thành phố '" + trimmedName + "' thành công!");
        return "redirect:/admin/regions";
    }

    @PostMapping("/provinces/{id}/delete")
    public String deleteProvince(
            @PathVariable("id") Integer id,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Province> provOpt = provinceRepository.findById(id);
        if (provOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tỉnh/thành phố");
            return "redirect:/admin/regions";
        }

        Province province = provOpt.get();
        try {
            provinceRepository.delete(province);
            provinceRepository.flush();
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa tỉnh/thành phố '" + province.getName() + "'!");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tỉnh/thành phố '" + province.getName() + "' đang có gian hàng hoặc sản phẩm liên kết nên không thể xóa.");
        }
        return "redirect:/admin/regions";
    }
}
