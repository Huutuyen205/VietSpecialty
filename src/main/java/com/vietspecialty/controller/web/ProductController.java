package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.ProductComment;
import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Region;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.CommentStatus;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.request.ProductFilterRequest;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.dto.response.ProductDetailResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.ProductCommentRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CatalogService;
import com.vietspecialty.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CatalogService catalogService;
    private final ProvinceRepository provinceRepository;
    private final ProductCommentRepository productCommentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

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
        String normalizedSort = "best-seller".equalsIgnoreCase(sort) ? "best_seller" : sort;

        ProductFilterRequest filter = new ProductFilterRequest(
                keyword, regionSlug, regionId, provinceSlug, provinceId,
                categorySlug, categoryId, minPrice, maxPrice,
                minRating4, hasOcop, shopId, normalizedSort, Math.max(0, page), Math.max(1, size)
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
        } else if (Boolean.TRUE.equals(hasOcop)) {
            pageTitle = "Tinh hoa Đặc sản đạt chuẩn OCOP 3-5 Sao";
        } else if (regionSlug != null && !regionSlug.isBlank()) {
            try {
                Region r = catalogService.getRegionBySlug(regionSlug);
                pageTitle = "Đặc sản " + r.getName();
            } catch (Exception ignored) {}
        } else if (provinceSlug != null && !provinceSlug.isBlank()) {
            provinceRepository.findBySlug(provinceSlug)
                    .ifPresent(p -> model.addAttribute("selectedProvince", p));
        }
        model.addAttribute("pageTitle", pageTitle);

        return "web/products";
    }

    @GetMapping("/products/{slug}")
    public Object productDetail(@PathVariable String slug, HttpServletRequest request, Model model) {
        ProductDetailResponse product;
        try {
            product = productService.getProductBySlug(slug);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.PRODUCT_NOT_FOUND) {
                ModelAndView mav = new ModelAndView("error/404", HttpStatus.NOT_FOUND);
                mav.addObject("errorCode", ex.getErrorCode().getCode());
                mav.addObject("errorMessage", ex.getMessage());
                mav.addObject("requestUri", request.getRequestURI());
                return mav;
            }
            throw ex;
        }
        productService.incrementViewCount(product.id());

        Integer categoryId = product.category() != null ? product.category().id() : null;
        Integer regionId = product.region() != null ? product.region().id() : null;
        List<ProductCardResponse> relatedProducts = productService.getRelatedProducts(product.id(), categoryId, regionId, 6);

        Page<ProductComment> commentsPage = productCommentRepository
                .findByProductIdAndParentIsNullAndStatusOrderByCreatedAtDesc(
                        product.id(), CommentStatus.VISIBLE, PageRequest.of(0, 20)
                );

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", relatedProducts);
        model.addAttribute("comments", commentsPage.getContent());
        model.addAttribute("commentCount", commentsPage.getTotalElements());

        return "web/product-detail";
    }

    @PostMapping(value = "/products/{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> postProductCommentJson(
            @PathVariable Long id,
            @RequestBody Map<String, Object> jsonBody,
            @CurrentUser CustomUserDetails userDetails
    ) {
        String text = null;
        Long pId = null;
        if (jsonBody != null) {
            if (jsonBody.get("content") != null) {
                text = jsonBody.get("content").toString();
            }
            if (jsonBody.get("parentId") != null) {
                try {
                    pId = Long.valueOf(jsonBody.get("parentId").toString());
                } catch (NumberFormatException ignored) {}
            }
        }
        return (ResponseEntity<?>) processProductComment(id, text, pId, userDetails, true, null);
    }

    @PostMapping("/products/{id}/comments")
    @Transactional
    public Object postProductCommentForm(
            @PathVariable Long id,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Long parentId,
            @CurrentUser CustomUserDetails userDetails,
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            RedirectAttributes redirectAttributes
    ) {
        boolean isJsonRequest = acceptHeader != null && acceptHeader.contains("application/json");
        return processProductComment(id, content, parentId, userDetails, isJsonRequest, redirectAttributes);
    }

    private Object processProductComment(
            Long id,
            String text,
            Long pId,
            CustomUserDetails userDetails,
            boolean isJsonRequest,
            RedirectAttributes redirectAttributes
    ) {
        if (userDetails == null) {
            if (isJsonRequest) {
                return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập để gửi câu hỏi"));
            }
            return "redirect:/login";
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (text == null || text.trim().isEmpty()) {
            if (isJsonRequest) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng nhập nội dung câu hỏi"));
            }
            redirectAttributes.addFlashAttribute("commentError", "Vui lòng nhập nội dung câu hỏi");
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập nội dung câu hỏi");
            return "redirect:/products/" + product.getSlug() + "#qa-section";
        }

        if (text.trim().length() > 1000) {
            if (isJsonRequest) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Nội dung câu hỏi không được vượt quá 1000 ký tự"));
            }
            redirectAttributes.addFlashAttribute("commentError", "Nội dung câu hỏi không được vượt quá 1000 ký tự");
            redirectAttributes.addFlashAttribute("errorMessage", "Nội dung câu hỏi không được vượt quá 1000 ký tự");
            return "redirect:/products/" + product.getSlug() + "#qa-section";
        }

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ProductComment parent = null;
        if (pId != null) {
            parent = productCommentRepository.findById(pId).orElse(null);
        }

        boolean isSellerReply = user.getRole() == Role.SELLER
                && product.getShop() != null
                && product.getShop().getOwner() != null
                && product.getShop().getOwner().getId().equals(user.getId());

        ProductComment comment = ProductComment.builder()
                .product(product)
                .user(user)
                .parent(parent)
                .content(text.trim())
                .isSellerReply(isSellerReply)
                .status(CommentStatus.VISIBLE)
                .build();

        ProductComment saved = productCommentRepository.save(comment);

        if (isJsonRequest) {
            return ResponseEntity.ok(ApiResponse.success("Đã gửi câu hỏi thành công", Map.of(
                    "id", saved.getId(),
                    "content", saved.getContent(),
                    "userFullName", user.getFullName(),
                    "isSellerReply", saved.getIsSellerReply(),
                    "createdAt", LocalDateTime.now().toString()
            )));
        }

        redirectAttributes.addFlashAttribute("commentSuccess", "Đã gửi câu hỏi về đặc sản cho gian hàng!");
        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi câu hỏi về đặc sản cho gian hàng!");
        return "redirect:/products/" + product.getSlug() + "#qa-section";
    }

    @GetMapping("/categories/{slug}")
    public String productsByCategory(@PathVariable String slug) {
        Category category = catalogService.getCategoryBySlug(slug);
        return "redirect:/products?categorySlug=" + category.getSlug();
    }

    @GetMapping("/regions")
    public String regionsExplorer() {
        return "redirect:/products";
    }

    @GetMapping("/regions/{slug}")
    public String productsByRegion(@PathVariable String slug) {
        Region region = catalogService.getRegionBySlug(slug);
        return "redirect:/products?regionSlug=" + region.getSlug();
    }

    @GetMapping("/provinces/{slug}")
    public String productsByProvince(@PathVariable String slug) {
        Province province = provinceRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy tỉnh/thành phố yêu cầu"));
        return "redirect:/products?provinceSlug=" + province.getSlug();
    }

    @GetMapping("/ocop")
    public String ocopProducts() {
        return "redirect:/products?hasOcop=true";
    }
}
