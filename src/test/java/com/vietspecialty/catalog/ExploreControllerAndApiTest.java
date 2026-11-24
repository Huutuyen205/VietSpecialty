package com.vietspecialty.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ExploreControllerAndApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/explore/regions trả về 4 vùng miền, 34 tỉnh thành và đúng colorHex")
    void testGetExploreRegionsApi_publicAccess() throws Exception {
        mockMvc.perform(get("/api/explore/regions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRegions").value(4))
                .andExpect(jsonPath("$.data.totalProvinces").value(34))
                .andExpect(jsonPath("$.data.regions", hasSize(4)))
                .andExpect(jsonPath("$.data.regions[0].slug").value("mien-bac"))
                .andExpect(jsonPath("$.data.regions[0].colorHex").value("#0F9D8A"))
                .andExpect(jsonPath("$.data.regions[0].provinces", hasSize(15)))
                .andExpect(jsonPath("$.data.regions[1].slug").value("mien-trung"))
                .andExpect(jsonPath("$.data.regions[1].colorHex").value("#E9A21F"))
                .andExpect(jsonPath("$.data.regions[1].provinces", hasSize(8)))
                .andExpect(jsonPath("$.data.regions[2].slug").value("tay-nguyen"))
                .andExpect(jsonPath("$.data.regions[2].colorHex").value("#8B5E3C"))
                .andExpect(jsonPath("$.data.regions[2].provinces", hasSize(3)))
                .andExpect(jsonPath("$.data.regions[3].slug").value("mien-nam"))
                .andExpect(jsonPath("$.data.regions[3].colorHex").value("#E8704A"))
                .andExpect(jsonPath("$.data.regions[3].provinces", hasSize(8)));
    }

    @Test
    @DisplayName("GET /api/explore/provinces/{slug} trả về chi tiết tỉnh hợp lệ (thai-nguyen, vinh-long, lai-chau)")
    void testGetExploreProvinceDetailApi_validProvince() throws Exception {
        mockMvc.perform(get("/api/explore/provinces/thai-nguyen"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("thai-nguyen"))
                .andExpect(jsonPath("$.data.regionSlug").value("mien-bac"))
                .andExpect(jsonPath("$.data.productsFilterUrl").value("/products?provinceSlug=thai-nguyen"))
                .andExpect(jsonPath("$.data.approvedShops", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.topSpecialties", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.topSpecialties[0].formattedPrice", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.topSpecialties[0].primaryImageUrl", not(emptyOrNullString())));

        mockMvc.perform(get("/api/explore/provinces/vinh-long"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("vinh-long"))
                .andExpect(jsonPath("$.data.productsFilterUrl").value("/products?provinceSlug=vinh-long"));

        mockMvc.perform(get("/api/explore/provinces/lai-chau"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("lai-chau"))
                .andExpect(jsonPath("$.data.activeProductCount").value(0))
                .andExpect(jsonPath("$.data.approvedShops", hasSize(0)))
                .andExpect(jsonPath("$.data.topSpecialties", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/explore/provinces/{unknown} trả về 404 JSON chuẩn ApiResponse")
    void testGetExploreProvinceDetailApi_unknownSlugReturns404Json() throws Exception {
        mockMvc.perform(get("/api/explore/provinces/tinh-khong-ton-tai-999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())));
    }

    @Test
    @DisplayName("GET /api/explore/provinces/{slug}?limit=abc trả về 400 Bad Request JSON chuẩn ApiResponse")
    void testGetExploreProvinceDetailApi_invalidLimitTypeReturns400Json() throws Exception {
        mockMvc.perform(get("/api/explore/provinces/thai-nguyen").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("limit")));
    }

    @Test
    @DisplayName("GET /explore render SSR với 4 vùng, 34 tỉnh, lọc region/province và fallback an toàn khi query lạ")
    void testGetExplorePage_guestParamsAndFallback() throws Exception {
        mockMvc.perform(get("/explore"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/explore"))
                .andExpect(model().attributeExists("overview", "pageTitle", "selectedProvinceDetail"))
                .andExpect(content().string(allOf(
                        containsString("Miền Bắc"),
                        containsString("Miền Trung"),
                        containsString("Tây Nguyên"),
                        containsString("Miền Nam"),
                        containsString("/products?provinceSlug=")
                )));

        mockMvc.perform(get("/explore").param("region", "mien-trung"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedRegionSlug", "mien-trung"))
                .andExpect(content().string(allOf(
                        containsString("Huế"),
                        containsString("Đà Nẵng")
                )));

        mockMvc.perform(get("/explore").param("province", "hue"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedRegionSlug", "mien-trung"))
                .andExpect(content().string(allOf(
                        containsString("Huế"),
                        containsString("/products?provinceSlug=hue")
                )));

        mockMvc.perform(get("/explore")
                        .param("region", "mien-bac")
                        .param("province", "hue"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedRegionSlug", "mien-trung"))
                .andExpect(content().string(allOf(
                        containsString("Huế"),
                        containsString("/products?provinceSlug=hue")
                )));

        mockMvc.perform(get("/explore")
                        .param("region", "khong-ton-tai")
                        .param("province", "tinh-ao-999"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/explore"));
    }

    @Test
    @WithUserDetails("buyer@vietspecialty.vn")
    @DisplayName("Người dùng đã đăng nhập (USER) truy cập /explore và /api/explore/regions thành công")
    void testGetExplorePageAndApi_authenticatedUserAccess() throws Exception {
        mockMvc.perform(get("/explore"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/explore"));

        mockMvc.perform(get("/api/explore/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalProvinces").value(34));
    }

    @Test
    @DisplayName("Trang chủ và sitemap.xml có liên kết /explore; GET /regions giữ nguyên redirect 3xx")
    void testHomeAndSitemapIncludeExploreAndRegionsRedirectPreserved() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/explore\"")));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://vietspecialty.vn/explore")));

        mockMvc.perform(get("/regions"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"));
    }
}
