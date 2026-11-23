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
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();

            // 1. Chỉ hiển thị sản phẩm ACTIVE và Gian hàng APPROVED
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            predicates.add(cb.equal(root.get("shop").get("status"), ShopStatus.APPROVED));

            // 2. Lọc theo từ khóa (tách token, hỗ trợ cả searchText không dấu và fallback trên name / shortDescription / originDetail)
            if (keyword != null && !keyword.isBlank()) {
                String normalized = VietnameseTextUtils.normalizeSearchText(keyword);
                String[] normTokens = normalized.split("\\s+");
                String[] rawTokens = keyword.trim().toLowerCase().split("\\s+");
                for (int i = 0; i < normTokens.length; i++) {
                    String normToken = normTokens[i];
                    String rawToken = i < rawTokens.length ? rawTokens[i] : normToken;
                    if (!normToken.isBlank()) {
                        String normPattern = "%" + normToken.toLowerCase() + "%";
                        String rawPattern = "%" + rawToken.toLowerCase() + "%";
                        predicates.add(cb.or(
                                cb.and(cb.isNotNull(root.get("searchText")), cb.like(cb.lower(root.get("searchText")), normPattern)),
                                cb.like(cb.lower(root.get("name")), rawPattern),
                                cb.like(cb.lower(root.get("name")), normPattern),
                                cb.and(cb.isNotNull(root.get("shortDescription")), cb.like(cb.lower(root.get("shortDescription")), rawPattern)),
                                cb.and(cb.isNotNull(root.get("originDetail")), cb.like(cb.lower(root.get("originDetail")), rawPattern))
                        ));
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
