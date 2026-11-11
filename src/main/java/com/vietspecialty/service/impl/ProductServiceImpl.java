package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.request.ProductCreateRequest;
import com.vietspecialty.dto.request.ProductFilterRequest;
import com.vietspecialty.dto.request.ProductUpdateRequest;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.dto.response.ProductDetailResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.mapper.ProductMapper;
import com.vietspecialty.repository.CategoryRepository;
import com.vietspecialty.repository.ProductImageRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.spec.ProductSpecification;
import com.vietspecialty.service.ProductService;
import com.vietspecialty.service.StorageService;
import com.vietspecialty.util.SlugUtils;
import com.vietspecialty.util.VietnameseTextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ShopRepository shopRepository;
    private final ProvinceRepository provinceRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    @Override
    public Page<ProductCardResponse> searchProducts(ProductFilterRequest req) {
        Sort sortOrder = switch (req.sort() != null ? req.sort() : "newest") {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "best_seller", "sold" -> Sort.by(Sort.Direction.DESC, "soldCount");
            case "rating" -> Sort.by(Sort.Direction.DESC, "avgRating");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        Pageable pageable = PageRequest.of(req.page(), req.size(), sortOrder);

        Specification<Product> spec = ProductSpecification.filter(
                req.keyword(),
                req.regionSlug(),
                req.regionId(),
                req.provinceSlug(),
                req.provinceId(),
                req.categorySlug(),
                req.categoryId(),
                req.minPrice(),
                req.maxPrice(),
                req.minRating4(),
                req.hasOcop(),
                req.shopId()
        );

        return productRepository.findAll(spec, pageable).map(ProductMapper::toCardResponse);
    }

    @Override
    public ProductDetailResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm đặc sản"));

        if (product.getShop() == null || product.getShop().getStatus() != ShopStatus.APPROVED) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Gian hàng của sản phẩm hiện không khả dụng");
        }

        return ProductMapper.toDetailResponse(product);
    }

    @Override
    public ProductDetailResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductMapper.toDetailResponse(product);
    }

    @Override
    public List<ProductCardResponse> getRelatedProducts(Long currentProductId, Integer categoryId, Integer regionId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "soldCount"));

        Specification<Product> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ProductStatus.ACTIVE),
                cb.equal(root.get("shop").get("status"), ShopStatus.APPROVED),
                cb.notEqual(root.get("id"), currentProductId),
                categoryId != null ? cb.equal(root.get("category").get("id"), categoryId) : cb.conjunction()
        );

        List<Product> products = productRepository.findAll(spec, pageable).getContent();
        return products.stream().map(ProductMapper::toCardResponse).toList();
    }

    @Override
    public List<ProductCardResponse> suggestProducts(String keyword, int limit) {
        if (keyword == null || keyword.trim().length() < 2) {
            return List.of();
        }
        ProductFilterRequest req = new ProductFilterRequest(
                keyword.trim(), null, null, null, null, null, null, null, null, null, null, null, "best_seller", 0, limit
        );
        return searchProducts(req).getContent();
    }

    @Override
    public List<ProductCardResponse> getFeaturedProducts(int limit) {
        ProductFilterRequest req = new ProductFilterRequest(
                null, null, null, null, null, null, null, null, null, true, null, null, "rating", 0, limit
        );
        return searchProducts(req).getContent();
    }

    @Override
    public List<ProductCardResponse> getBestSellers(int limit) {
        ProductFilterRequest req = new ProductFilterRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, "best_seller", 0, limit
        );
        return searchProducts(req).getContent();
    }

    @Override
    public List<ProductCardResponse> getOcopProducts(int limit) {
        ProductFilterRequest req = new ProductFilterRequest(
                null, null, null, null, null, null, null, null, null, null, true, null, "newest", 0, limit
        );
        return searchProducts(req).getContent();
    }

    @Override
    public List<ProductCardResponse> getNewestProducts(int limit) {
        ProductFilterRequest req = new ProductFilterRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, "newest", 0, limit
        );
        return searchProducts(req).getContent();
    }

    @Override
    @Transactional
    public void incrementViewCount(Long productId) {
        log.debug("Incrementing view count for product id: {}", productId);
    }

    // ==========================================
    // KÊNH NGƯỜI BÁN (SELLER)
    // ==========================================

    @Override
    @Transactional
    public ProductDetailResponse createProduct(Long shopId, ProductCreateRequest req, List<MultipartFile> imageFiles) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_APPROVED, "Gian hàng của bạn chưa được kích hoạt để đăng bán sản phẩm");
        }

        Province province = provinceRepository.findById(req.provinceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Tỉnh/thành phố không hợp lệ"));

        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh mục sản phẩm không hợp lệ"));

        List<MultipartFile> validImages = (imageFiles != null)
                ? imageFiles.stream().filter(f -> f != null && !f.isEmpty()).toList()
                : List.of();

        if (validImages.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_REQUIRED);
        }

        // Tạo slug duy nhất
        String baseSlug = SlugUtils.toSlug(req.name());
        String slug = baseSlug;
        int counter = 1;
        while (productRepository.findBySlugAndStatus(slug, ProductStatus.ACTIVE).isPresent()) {
            slug = baseSlug + "-" + counter++;
        }

        String searchText = VietnameseTextUtils.toSearchableText(
                req.name(),
                req.originDetail(),
                province.getName(),
                category.getName()
        );

        Product product = Product.builder()
                .shop(shop)
                .province(province)
                .category(category)
                .name(req.name().trim())
                .slug(slug)
                .shortDescription(req.shortDescription() != null ? req.shortDescription().trim() : null)
                .description(req.description().trim())
                .price(req.price())
                .stock(req.stock())
                .unit(req.unit().trim())
                .weightGram(req.weightGram())
                .originDetail(req.originDetail().trim())
                .shelfLife(req.shelfLife() != null ? req.shelfLife().trim() : null)
                .storageInstructions(req.storageInstructions() != null ? req.storageInstructions().trim() : null)
                .ocopRating(req.ocopRating())
                .soldCount(0)
                .avgRating(new BigDecimal("5.00"))
                .reviewCount(0)
                .status(ProductStatus.ACTIVE)
                .searchText(searchText)
                .images(new ArrayList<>())
                .build();

        Product savedProduct = productRepository.save(product);

        for (int i = 0; i < validImages.size(); i++) {
            MultipartFile file = validImages.get(i);
            String imageUrl = storageService.uploadImage(file, "products");
            ProductImage pImg = ProductImage.builder()
                    .product(savedProduct)
                    .imageUrl(imageUrl)
                    .isPrimary(i == 0)
                    .sortOrder(i)
                    .build();
            savedProduct.addImage(pImg);
        }

        Product updatedProduct = productRepository.save(savedProduct);
        log.info("Product created successfully: id={}, name={}, shopId={}", updatedProduct.getId(), updatedProduct.getName(), shopId);

        return ProductMapper.toDetailResponse(updatedProduct);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(Long shopId, Long productId, ProductUpdateRequest req, List<MultipartFile> newImageFiles) {
        Product product = productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm trong gian hàng của bạn"));

        Province province = provinceRepository.findById(req.provinceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Tỉnh thành không hợp lệ"));

        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh mục không hợp lệ"));

        product.setName(req.name().trim());
        product.setShortDescription(req.shortDescription() != null ? req.shortDescription().trim() : null);
        product.setDescription(req.description().trim());
        product.setPrice(req.price());
        product.setStock(req.stock());
        product.setUnit(req.unit().trim());
        product.setWeightGram(req.weightGram());
        product.setOriginDetail(req.originDetail().trim());
        product.setShelfLife(req.shelfLife() != null ? req.shelfLife().trim() : null);
        product.setStorageInstructions(req.storageInstructions() != null ? req.storageInstructions().trim() : null);
        product.setOcopRating(req.ocopRating());
        product.setProvince(province);
        product.setCategory(category);

        product.setSearchText(VietnameseTextUtils.toSearchableText(
                req.name(), req.originDetail(), province.getName(), category.getName()
        ));

        if (newImageFiles != null) {
            int currentCount = product.getImages() != null ? product.getImages().size() : 0;
            for (MultipartFile file : newImageFiles) {
                if (file != null && !file.isEmpty()) {
                    String imageUrl = storageService.uploadImage(file, "products");
                    ProductImage pImg = ProductImage.builder()
                            .product(product)
                            .imageUrl(imageUrl)
                            .isPrimary(currentCount == 0)
                            .sortOrder(currentCount++)
                            .build();
                    product.addImage(pImg);
                }
            }
        }

        Product savedProduct = productRepository.save(product);
        log.info("Product updated successfully: id={}, shopId={}", productId, shopId);
        return ProductMapper.toDetailResponse(savedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long shopId, Long productId) {
        Product product = productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm trong gian hàng"));

        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);
        log.info("Product soft-deleted: id={}, shopId={}", productId, shopId);
    }

    @Override
    @Transactional
    public void toggleProductStatus(Long shopId, Long productId) {
        Product product = productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm trong gian hàng"));

        if (product.getStatus() == ProductStatus.ACTIVE) {
            product.setStatus(ProductStatus.HIDDEN);
        } else if (product.getStatus() == ProductStatus.HIDDEN) {
            product.setStatus(ProductStatus.ACTIVE);
        }
        productRepository.save(product);
        log.info("Product status toggled: id={}, newStatus={}", productId, product.getStatus());
    }

    @Override
    public Page<ProductCardResponse> getSellerProducts(Long shopId, String keyword, ProductStatus status, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("shop").get("id"), shopId));
            predicates.add(cb.notEqual(root.get("status"), ProductStatus.DELETED));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (keyword != null && !keyword.isBlank()) {
                String normalized = VietnameseTextUtils.normalizeSearchText(keyword);
                predicates.add(cb.like(cb.lower(root.get("searchText")), "%" + normalized.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return productRepository.findAll(spec, pageable).map(ProductMapper::toCardResponse);
    }
}
