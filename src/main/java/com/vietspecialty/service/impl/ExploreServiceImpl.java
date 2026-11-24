package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.ProductImage;
import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Region;
import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.dto.response.ExploreProvinceDetailResponse;
import com.vietspecialty.dto.response.ExploreProvinceSummaryResponse;
import com.vietspecialty.dto.response.ExploreRegionSummaryResponse;
import com.vietspecialty.dto.response.ExploreRegionsResponse;
import com.vietspecialty.dto.response.ExploreShopSummaryResponse;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.ProductImageRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.RegionRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.service.ExploreService;
import com.vietspecialty.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExploreServiceImpl implements ExploreService {

    private static final String DEFAULT_PRODUCT_IMAGE = "/images/demo/placeholder.svg";

    private final RegionRepository regionRepository;
    private final ProvinceRepository provinceRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ShopRepository shopRepository;

    @Override
    public ExploreRegionsResponse getExploreRegionsOverview() {
        List<Region> regions = regionRepository.findAllByOrderBySortOrderAsc();
        List<Province> provinces = provinceRepository.findAllByOrderByNameAsc();

        Map<Integer, ProductRepository.ProvinceProductStatsProjection> productStatsMap = productRepository
                .countActiveAndOcopProductsByProvince()
                .stream()
                .collect(Collectors.toMap(
                        ProductRepository.ProvinceProductStatsProjection::getProvinceId,
                        Function.identity()
                ));

        Map<Integer, Long> shopCountsMap = shopRepository
                .countApprovedShopsByProvince()
                .stream()
                .collect(Collectors.toMap(
                        ShopRepository.ProvinceShopCountProjection::getProvinceId,
                        ShopRepository.ProvinceShopCountProjection::getApprovedShopCount
                ));

        Map<Integer, List<Province>> provincesByRegionId = new HashMap<>();
        for (Province province : provinces) {
            provincesByRegionId
                    .computeIfAbsent(province.getRegion().getId(), ignored -> new ArrayList<>())
                    .add(province);
        }

        List<ExploreRegionSummaryResponse> regionSummaries = new ArrayList<>(regions.size());
        long grandTotalProvinces = 0L;
        long grandTotalActiveProducts = 0L;
        long grandTotalOcopProducts = 0L;
        long grandTotalApprovedShops = 0L;

        for (Region region : regions) {
            List<Province> regionProvinces = provincesByRegionId.getOrDefault(region.getId(), List.of());
            List<ExploreProvinceSummaryResponse> provinceSummaries = new ArrayList<>(regionProvinces.size());

            long regionActiveProducts = 0L;
            long regionOcopProducts = 0L;
            long regionApprovedShops = 0L;

            for (Province province : regionProvinces) {
                ProductRepository.ProvinceProductStatsProjection stats = productStatsMap.get(province.getId());
                long activeCount = stats != null ? stats.getActiveProductCount() : 0L;
                long ocopCount = stats != null ? stats.getOcopProductCount() : 0L;
                long shopCount = shopCountsMap.getOrDefault(province.getId(), 0L);

                regionActiveProducts += activeCount;
                regionOcopProducts += ocopCount;
                regionApprovedShops += shopCount;

                provinceSummaries.add(new ExploreProvinceSummaryResponse(
                        province.getId(),
                        province.getName(),
                        province.getSlug(),
                        region.getId(),
                        region.getName(),
                        region.getSlug(),
                        region.getColorHex(),
                        activeCount,
                        ocopCount,
                        shopCount
                ));
            }

            long provinceCount = provinceSummaries.size();
            grandTotalProvinces += provinceCount;
            grandTotalActiveProducts += regionActiveProducts;
            grandTotalOcopProducts += regionOcopProducts;
            grandTotalApprovedShops += regionApprovedShops;

            regionSummaries.add(new ExploreRegionSummaryResponse(
                    region.getId(),
                    region.getName(),
                    region.getSlug(),
                    region.getDescription(),
                    region.getColorHex(),
                    region.getSortOrder(),
                    provinceCount,
                    regionActiveProducts,
                    regionOcopProducts,
                    regionApprovedShops,
                    List.copyOf(provinceSummaries)
            ));
        }

        return new ExploreRegionsResponse(
                regionSummaries.size(),
                grandTotalProvinces,
                grandTotalActiveProducts,
                grandTotalOcopProducts,
                grandTotalApprovedShops,
                List.copyOf(regionSummaries)
        );
    }

    @Override
    public ExploreProvinceDetailResponse getProvinceDetail(String slug, int topLimit) {
        if (slug == null || slug.isBlank()) {
            throw new BusinessException(ErrorCode.PROVINCE_NOT_FOUND);
        }
        int clampedLimit = Math.max(1, Math.min(24, topLimit));

        Province province = provinceRepository.findBySlugWithRegion(slug.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROVINCE_NOT_FOUND));
        Region region = province.getRegion();

        Optional<ProductRepository.ProvinceProductStatsProjection> statsOpt =
                productRepository.countActiveAndOcopProductsForProvince(province.getId());
        long activeProductCount = statsOpt.map(ProductRepository.ProvinceProductStatsProjection::getActiveProductCount).orElse(0L);
        long ocopProductCount = statsOpt.map(ProductRepository.ProvinceProductStatsProjection::getOcopProductCount).orElse(0L);

        List<ExploreShopSummaryResponse> approvedShops = shopRepository
                .findApprovedShopSummariesByProvinceId(province.getId())
                .stream()
                .map(row -> new ExploreShopSummaryResponse(
                        row.getId(),
                        row.getName(),
                        row.getSlug(),
                        row.getLogoUrl(),
                        row.getAddressDetail(),
                        row.getProvinceName(),
                        row.getActiveProductCount()
                ))
                .toList();

        List<Product> topProducts = productRepository.findTopActiveByProvinceId(
                province.getId(),
                PageRequest.of(0, clampedLimit)
        );

        Map<Long, String> primaryImageByProductId = resolvePrimaryImages(topProducts);
        List<ProductCardResponse> topSpecialties = topProducts.stream()
                .map(product -> toCardResponse(product, primaryImageByProductId.getOrDefault(product.getId(), DEFAULT_PRODUCT_IMAGE)))
                .toList();

        return new ExploreProvinceDetailResponse(
                province.getId(),
                province.getName(),
                province.getSlug(),
                region.getId(),
                region.getName(),
                region.getSlug(),
                region.getColorHex(),
                activeProductCount,
                ocopProductCount,
                approvedShops.size(),
                "/products?provinceSlug=" + province.getSlug(),
                approvedShops,
                topSpecialties
        );
    }

    private Map<Long, String> resolvePrimaryImages(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<ProductImage> images = productImageRepository.findByProductIdInOrderBySortOrderAsc(productIds);
        Map<Long, String> primaryImages = new HashMap<>();
        for (ProductImage img : images) {
            Long productId = img.getProduct().getId();
            if (Boolean.TRUE.equals(img.getIsPrimary())) {
                primaryImages.put(productId, img.getImageUrl());
            } else {
                primaryImages.putIfAbsent(productId, img.getImageUrl());
            }
        }
        return primaryImages;
    }

    private ProductCardResponse toCardResponse(Product product, String primaryImageUrl) {
        Province province = product.getProvince();
        Region region = province.getRegion();
        Shop shop = product.getShop();

        return new ProductCardResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getPrice(),
                MoneyUtils.formatVnd(product.getPrice()),
                product.getUnit(),
                product.getStock(),
                product.getOcopRating(),
                product.getSoldCount(),
                product.getAvgRating(),
                product.getReviewCount(),
                primaryImageUrl,
                product.getOriginDetail(),
                province.getName(),
                province.getSlug(),
                region.getName(),
                region.getSlug(),
                region.getColorHex(),
                shop.getId(),
                shop.getName(),
                shop.getSlug(),
                product.getStatus()
        );
    }
}
