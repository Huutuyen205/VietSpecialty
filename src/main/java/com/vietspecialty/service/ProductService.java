package com.vietspecialty.service;

import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.dto.request.ProductCreateRequest;
import com.vietspecialty.dto.request.ProductFilterRequest;
import com.vietspecialty.dto.request.ProductUpdateRequest;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.dto.response.ProductDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {

    Page<ProductCardResponse> searchProducts(ProductFilterRequest filterRequest);

    ProductDetailResponse getProductBySlug(String slug);

    ProductDetailResponse getProductById(Long id);

    List<ProductCardResponse> getRelatedProducts(Long currentProductId, Integer categoryId, Integer regionId, int limit);

    List<ProductCardResponse> suggestProducts(String keyword, int limit);

    List<ProductCardResponse> getFeaturedProducts(int limit);

    List<ProductCardResponse> getBestSellers(int limit);

    List<ProductCardResponse> getOcopProducts(int limit);

    List<ProductCardResponse> getNewestProducts(int limit);

    void incrementViewCount(Long productId);

    // Dành riêng cho Kênh Người Bán (Seller)
    ProductDetailResponse createProduct(Long shopId, ProductCreateRequest req, List<MultipartFile> imageFiles);

    ProductDetailResponse updateProduct(Long shopId, Long productId, ProductUpdateRequest req, List<MultipartFile> newImageFiles);

    void deleteProduct(Long shopId, Long productId);

    void toggleProductStatus(Long shopId, Long productId);

    Page<ProductCardResponse> getSellerProducts(Long shopId, String keyword, ProductStatus status, Pageable pageable);
}
