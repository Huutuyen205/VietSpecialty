package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"images", "shop", "province", "category"})
    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"images", "shop", "province", "category"})
    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    Page<Product> findByShopIdAndStatusNot(Long shopId, ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.shop.id = :shopId AND p.stock <= 5 AND p.status = 'ACTIVE' ORDER BY p.stock ASC")
    List<Product> findLowStockProductsByShopId(@Param("shopId") Long shopId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.shop.id = :shopId AND p.status = 'ACTIVE' ORDER BY p.soldCount DESC")
    List<Product> findTopSellingByShopId(@Param("shopId") Long shopId, Pageable pageable);

    long countByShopId(Long shopId);

    long countByShopIdAndStatus(Long shopId, ProductStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity, p.version = p.version + 1 " +
           "WHERE p.id = :id AND p.stock >= :quantity AND p.status = 'ACTIVE'")
    int decrementStockAtomic(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity, p.version = p.version + 1 WHERE p.id = :id")
    int incrementStockAtomic(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.soldCount = p.soldCount + :quantity WHERE p.id = :id")
    int incrementSoldCount(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.avgRating = :avgRating, p.reviewCount = :reviewCount WHERE p.id = :id")
    void updateProductRatingStats(@Param("id") Long id, @Param("avgRating") java.math.BigDecimal avgRating, @Param("reviewCount") Integer reviewCount);

    interface ProvinceProductStatsProjection {
        Integer getProvinceId();
        long getActiveProductCount();
        long getOcopProductCount();
    }

    @Query("""
        SELECT p.province.id AS provinceId,
               COUNT(p.id) AS activeProductCount,
               COALESCE(SUM(CASE WHEN p.ocopRating IS NOT NULL AND p.ocopRating >= 3 THEN 1 ELSE 0 END), 0) AS ocopProductCount
        FROM Product p
        WHERE p.status = com.vietspecialty.domain.enums.ProductStatus.ACTIVE
          AND p.shop.status = com.vietspecialty.domain.enums.ShopStatus.APPROVED
        GROUP BY p.province.id
        """)
    List<ProvinceProductStatsProjection> countActiveAndOcopProductsByProvince();

    @Query("""
        SELECT p.province.id AS provinceId,
               COUNT(p.id) AS activeProductCount,
               COALESCE(SUM(CASE WHEN p.ocopRating IS NOT NULL AND p.ocopRating >= 3 THEN 1 ELSE 0 END), 0) AS ocopProductCount
        FROM Product p
        WHERE p.province.id = :provinceId
          AND p.status = com.vietspecialty.domain.enums.ProductStatus.ACTIVE
          AND p.shop.status = com.vietspecialty.domain.enums.ShopStatus.APPROVED
        GROUP BY p.province.id
        """)
    Optional<ProvinceProductStatsProjection> countActiveAndOcopProductsForProvince(@Param("provinceId") Integer provinceId);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.shop s
        JOIN FETCH p.province pr
        JOIN FETCH pr.region r
        WHERE pr.id = :provinceId
          AND p.status = com.vietspecialty.domain.enums.ProductStatus.ACTIVE
          AND s.status = com.vietspecialty.domain.enums.ShopStatus.APPROVED
        ORDER BY p.soldCount DESC, p.avgRating DESC, p.id DESC
        """)
    List<Product> findTopActiveByProvinceId(@Param("provinceId") Integer provinceId, Pageable pageable);
}

