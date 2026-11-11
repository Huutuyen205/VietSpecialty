package com.vietspecialty.repository.spec;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.util.VietnameseTextUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProductSpecification {

    private ProductSpecification() {
        // Utility class
    }

    public static Specification<Product> filter(
            String keyword,
            String regionSlug,
            Integer regionId,
            String provinceSlug,
            Integer provinceId,
            String categorySlug,
            Integer categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean minRating4,
            Boolean hasOcop,
            Long shopId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Chỉ hiển thị sản phẩm ACTIVE và Gian hàng APPROVED
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            predicates.add(cb.equal(root.get("shop").get("status"), ShopStatus.APPROVED));

            // 2. Lọc theo từ khóa (tách token, mỗi token phải khớp search_text)
            if (keyword != null && !keyword.isBlank()) {
                String normalized = VietnameseTextUtils.normalizeSearchText(keyword);
                String[] tokens = normalized.split("\\s+");
                for (String token : tokens) {
                    if (!token.isBlank()) {
                        predicates.add(cb.like(cb.lower(root.get("searchText")), "%" + token.toLowerCase() + "%"));
                    }
                }
            }

            // 3. Lọc theo vùng miền (slug hoặc id)
            if (regionSlug != null && !regionSlug.isBlank()) {
                predicates.add(cb.equal(root.get("province").get("region").get("slug"), regionSlug));
            } else if (regionId != null) {
                predicates.add(cb.equal(root.get("province").get("region").get("id"), regionId));
            }

            // 4. Lọc theo tỉnh thành (slug hoặc id)
            if (provinceSlug != null && !provinceSlug.isBlank()) {
                predicates.add(cb.equal(root.get("province").get("slug"), provinceSlug));
            } else if (provinceId != null) {
                predicates.add(cb.equal(root.get("province").get("id"), provinceId));
            }

            // 5. Lọc theo danh mục (slug hoặc id)
            if (categorySlug != null && !categorySlug.isBlank()) {
                predicates.add(cb.equal(root.get("category").get("slug"), categorySlug));
            } else if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            // 6. Khoảng giá (minPrice, maxPrice)
            if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) > 0) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            // 7. Đánh giá >= 4 sao
            if (Boolean.TRUE.equals(minRating4)) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("avgRating"), new BigDecimal("4.0")));
            }

            // 8. Có chứng nhận OCOP
            if (Boolean.TRUE.equals(hasOcop)) {
                predicates.add(cb.isNotNull(root.get("ocopRating")));
                predicates.add(cb.greaterThanOrEqualTo(root.get("ocopRating"), 3));
            }

            // 9. Lọc theo gian hàng (shopId)
            if (shopId != null) {
                predicates.add(cb.equal(root.get("shop").get("id"), shopId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
