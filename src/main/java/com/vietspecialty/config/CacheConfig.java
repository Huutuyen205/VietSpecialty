package com.vietspecialty.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    public static final String CACHE_REGIONS = "regions";
    public static final String CACHE_PROVINCES = "provinces";
    public static final String CACHE_CATEGORIES = "categories";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(CACHE_REGIONS, CACHE_PROVINCES, CACHE_CATEGORIES);
    }
}

