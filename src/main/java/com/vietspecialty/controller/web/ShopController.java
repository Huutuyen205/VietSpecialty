package com.vietspecialty.controller.web;

import com.vietspecialty.dto.request.ProductFilterRequest;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ProductService;
import com.vietspecialty.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final ProductService productService;
    private final CatalogService catalogService;

    @GetMapping("/shops/{slug}")
    public String shopDetail(
            @PathVariable String slug,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "16") Integer size,
            Model model
    ) {
        ShopResponse shop = shopService.getShopBySlug(slug);

        ProductFilterRequest filter = new ProductFilterRequest(
                keyword, null, null, null, null,
                null, categoryId, null, null,
                null, null, shop.id(), sort, page, size
        );

        Page<ProductCardResponse> productPage = productService.searchProducts(filter);

        model.addAttribute("shop", shop);
        model.addAttribute("productPage", productPage);
        model.addAttribute("filter", filter);
        model.addAttribute("categories", catalogService.getAllActiveCategories());

        return "web/shop-detail";
    }
}

