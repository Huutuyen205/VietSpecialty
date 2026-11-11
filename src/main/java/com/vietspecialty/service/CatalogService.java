package com.vietspecialty.service;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Region;

import java.util.List;

public interface CatalogService {

    List<Region> getAllRegions();

    Region getRegionBySlug(String slug);

    List<Province> getProvincesByRegionId(Integer regionId);

    List<Province> getAllProvinces();

    List<Category> getAllActiveCategories();

    Category getCategoryBySlug(String slug);
}
