package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.enums.ShopStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findByOwnerId(Long ownerId);

    Optional<Shop> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByOwnerId(Long ownerId);

    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);

    long countByStatus(ShopStatus status);

    interface ProvinceShopCountProjection {
        Integer getProvinceId();
        long getApprovedShopCount();
    }

    interface ApprovedShopSummaryProjection {
        Long getId();
        String getName();
        String getSlug();
        String getLogoUrl();
        String getAddressDetail();
        String getProvinceName();
        long getActiveProductCount();
    }

    @Query("""
        SELECT s.province.id AS provinceId, COUNT(s.id) AS approvedShopCount
        FROM Shop s
        WHERE s.status = com.vietspecialty.domain.enums.ShopStatus.APPROVED
        GROUP BY s.province.id
        """)
    List<ProvinceShopCountProjection> countApprovedShopsByProvince();

    @Query("""
        SELECT s.id AS id,
               s.name AS name,
               s.slug AS slug,
               s.logoUrl AS logoUrl,
               s.addressDetail AS addressDetail,
               pr.name AS provinceName,
               COUNT(p.id) AS activeProductCount
        FROM Shop s
        JOIN s.province pr
        LEFT JOIN Product p ON p.shop.id = s.id AND p.status = com.vietspecialty.domain.enums.ProductStatus.ACTIVE
        WHERE pr.id = :provinceId
          AND s.status = com.vietspecialty.domain.enums.ShopStatus.APPROVED
        GROUP BY s.id, s.name, s.slug, s.logoUrl, s.addressDetail, pr.name
        ORDER BY COUNT(p.id) DESC, s.name ASC
        """)
    List<ApprovedShopSummaryProjection> findApprovedShopSummariesByProvinceId(@Param("provinceId") Integer provinceId);
}

