package com.vietspecialty.controller.api;

import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductApiController {

    private final ProductService productService;
    private final CatalogService catalogService;

    @GetMapping("/products/suggest")
    public ResponseEntity<ApiResponse<List<ProductCardResponse>>> suggestProducts(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(defaultValue = "6") int limit
    ) {
        String searchTerm = (query != null && !query.isBlank()) ? query : (keyword != null ? keyword : "");
        List<ProductCardResponse> suggestions = productService.suggestProducts(searchTerm, limit);
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> getProvinces(
            @RequestParam(required = false) Integer regionId
    ) {
        List<Province> provinces = catalogService.getProvincesByRegionId(regionId);
        List<ProvinceResponse> response = provinces.stream()
                .map(p -> new ProvinceResponse(p.getId(), p.getName(), p.getSlug()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    public record ProvinceResponse(Integer id, String name, String slug) {}
}
