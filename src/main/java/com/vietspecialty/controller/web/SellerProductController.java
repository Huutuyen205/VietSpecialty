package com.vietspecialty.controller.web;

import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.dto.request.ProductCreateRequest;
import com.vietspecialty.dto.request.ProductUpdateRequest;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.dto.response.ProductDetailResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ProductService;
import com.vietspecialty.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/seller/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerProductController {

    private final ProductService productService;
    private final ShopService shopService;
    private final CatalogService catalogService;

    @GetMapping
    public String listProducts(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        ProductStatus parsedStatus = parseProductStatus(status);
        Page<ProductCardResponse> products = productService.getSellerProducts(
                shop.id(), keyword, parsedStatus, PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        model.addAttribute("shop", shop);
        model.addAttribute("products", products);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", parsedStatus);
        model.addAttribute("activeNav", "products");

        return "seller/products/index";
    }

    private ProductStatus parseProductStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        String normalized = statusStr.trim().toUpperCase();
        if ("INACTIVE".equals(normalized) || "HIDDEN".equals(normalized)) {
            return ProductStatus.HIDDEN;
        }
        try {
            return ProductStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @GetMapping({"/create", "/new"})
    public String createProductForm(@CurrentUser CustomUserDetails userDetails, Model model) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());

        model.addAttribute("shop", shop);
        model.addAttribute("productRequest", new ProductCreateRequest("", "", "", null, null, "", null, "", "", "", null, null, null));
        model.addAttribute("categories", catalogService.getAllActiveCategories());
        model.addAttribute("provinces", catalogService.getAllProvinces());
        model.addAttribute("isEdit", false);
        model.addAttribute("activeNav", "products");

        return "seller/products/form";
    }

    @PostMapping({"", "/create", "/new"})
    public String handleCreateProduct(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @ModelAttribute("productRequest") ProductCreateRequest req,
            BindingResult bindingResult,
            @RequestParam(value = "images", required = false) List<MultipartFile> imageFiles,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());

        if (bindingResult.hasErrors()) {
            model.addAttribute("shop", shop);
            model.addAttribute("categories", catalogService.getAllActiveCategories());
            model.addAttribute("provinces", catalogService.getAllProvinces());
            model.addAttribute("isEdit", false);
            model.addAttribute("activeNav", "products");
            return "seller/products/form";
        }

        try {
            productService.createProduct(shop.id(), req, imageFiles);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm đặc sản thành công!");
            return "redirect:/seller/products";
        } catch (BusinessException e) {
            model.addAttribute("shop", shop);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("categories", catalogService.getAllActiveCategories());
            model.addAttribute("provinces", catalogService.getAllProvinces());
            model.addAttribute("isEdit", false);
            model.addAttribute("activeNav", "products");
            return "seller/products/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editProductForm(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            Model model
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        ProductDetailResponse product = productService.getProductById(id);

        // Chống IDOR: kiểm tra sản phẩm thuộc shop
        if (product.shop() == null || !product.shop().id().equals(shop.id())) {
            throw new BusinessException(com.vietspecialty.exception.ErrorCode.ACCESS_DENIED);
        }

        Integer provinceId = product.province() != null ? product.province().id() : null;
        Integer categoryId = product.category() != null ? product.category().id() : null;

        ProductUpdateRequest req = new ProductUpdateRequest(
                product.name(),
                product.shortDescription(),
                product.description(),
                product.price(),
                product.stock(),
                product.unit(),
                product.weightGram(),
                product.originDetail(),
                product.shelfLife(),
                product.storageInstructions(),
                product.ocopRating(),
                provinceId,
                categoryId
        );

        model.addAttribute("shop", shop);
        model.addAttribute("product", product);
        model.addAttribute("productRequest", req);
        model.addAttribute("categories", catalogService.getAllActiveCategories());
        model.addAttribute("provinces", catalogService.getAllProvinces());
        model.addAttribute("isEdit", true);
        model.addAttribute("activeNav", "products");

        return "seller/products/form";
    }

    @PostMapping("/{id}/edit")
    public String handleUpdateProduct(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @ModelAttribute("productRequest") ProductUpdateRequest req,
            BindingResult bindingResult,
            @RequestParam(value = "newImages", required = false) List<MultipartFile> newImageFiles,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());

        if (bindingResult.hasErrors()) {
            model.addAttribute("shop", shop);
            model.addAttribute("product", productService.getProductById(id));
            model.addAttribute("categories", catalogService.getAllActiveCategories());
            model.addAttribute("provinces", catalogService.getAllProvinces());
            model.addAttribute("isEdit", true);
            model.addAttribute("activeNav", "products");
            return "seller/products/form";
        }

        try {
            productService.updateProduct(shop.id(), id, req, newImageFiles);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin đặc sản thành công!");
            return "redirect:/seller/products";
        } catch (BusinessException e) {
            model.addAttribute("shop", shop);
            model.addAttribute("product", productService.getProductById(id));
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("categories", catalogService.getAllActiveCategories());
            model.addAttribute("provinces", catalogService.getAllProvinces());
            model.addAttribute("isEdit", true);
            model.addAttribute("activeNav", "products");
            return "seller/products/form";
        }
    }

    @PostMapping({"/{id}/status", "/{id}/toggle"})
    public String toggleStatus(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        productService.toggleProductStatus(shop.id(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái hiển thị của sản phẩm!");
        return "redirect:/seller/products";
    }

    @PostMapping("/{id}/delete")
    public String deleteProduct(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        productService.deleteProduct(shop.id(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi danh mục gian hàng!");
        return "redirect:/seller/products";
    }
}

