package com.vietspecialty.controller.web;

import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CatalogService catalogService;
    private final ProductService productService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "Hương vị quê nhà, gửi trọn yêu thương");
        model.addAttribute("regions", catalogService.getAllRegions());
        model.addAttribute("categories", catalogService.getAllActiveCategories());
        model.addAttribute("ocopProducts", productService.getOcopProducts(8));
        model.addAttribute("bestSellers", productService.getBestSellers(8));
        model.addAttribute("newestProducts", productService.getNewestProducts(8));
        return "web/home";
    }
}
