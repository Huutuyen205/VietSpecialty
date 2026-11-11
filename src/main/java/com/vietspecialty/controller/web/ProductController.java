package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.domain.entity.Region;
import com.vietspecialty.dto.request.ProductFilterRequest;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.dto.response.ProductDetailResponse;
import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CatalogService catalogService;

    @GetMapping("/products")
    public String productList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String regionSlug,
            @RequestParam(required = false) Integer regionId,
            @RequestParam(required = false) String provinceSlug,
            @RequestParam(required = false) Integer provinceId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean minRating4,
            @RequestParam(required = false) Boolean hasOcop,
            @RequestParam(required = false) Long shopId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "16") Integer size,
            Model model
    ) {
        ProductFilterRequest filter = new ProductFilterRequest(
                keyword, regionSlug, regionId, provinceSlug, provinceId,
                categorySlug, categoryId, minPrice, maxPrice,
                minRating4, hasOcop, shopId, sort, page, size
        );

        Page<ProductCardResponse> productPage = productService.searchProducts(filter);

        model.addAttribute("productPage", productPage);
        model.addAttribute("filter", filter);
        model.addAttribute("categories", catalogService.getAllActiveCategories());
        model.addAttribute("regions", catalogService.getAllRegions());
        model.addAttribute("provinces", catalogService.getAllProvinces());

        // Tiêu đề trang động
        String pageTitle = "Tất cả đặc sản vùng miền";
        if (keyword != null && !keyword.isBlank()) {
            pageTitle = "Kết quả tìm kiếm cho \"" + keyword + "\"";
        }
        model.addAttribute("pageTitle", pageTitle);

        return "web/products";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, Model model) {
        ProductDetailResponse product = productService.getProductBySlug(slug);
        productService.incrementViewCount(product.id());

        // Lấy sản phẩm liên quan (cùng danh mục hoặc vùng miền)
        Integer categoryId = product.category() != null ? product.category().id() : null;
        Integer regionId = product.region() != null ? product.region().id() : null;
        List<ProductCardResponse> relatedProducts = productService.getRelatedProducts(product.id(), categoryId, regionId, 6);

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", relatedProducts);

        return "web/product-detail";
    }

    @GetMapping("/categories/{slug}")
    public String productsByCategory(@PathVariable String slug, Model model) {
        Category category = catalogService.getCategoryBySlug(slug);
        return "redirect:/products?categorySlug=" + category.getSlug();
    }

    @GetMapping("/regions/{slug}")
    public String productsByRegion(@PathVariable String slug, Model model) {
        Region region = catalogService.getRegionBySlug(slug);
        return "redirect:/products?regionSlug=" + region.getSlug();
    }
}

