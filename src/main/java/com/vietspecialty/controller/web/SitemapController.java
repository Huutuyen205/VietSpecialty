package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.repository.CategoryRepository;
import com.vietspecialty.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class SitemapController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Trang chủ
        xml.append("  <url>\n");
        xml.append("    <loc>https://vietspecialty.vn/</loc>\n");
        xml.append("    <changefreq>daily</changefreq>\n");
        xml.append("    <priority>1.0</priority>\n");
        xml.append("  </url>\n");

        // Bản đồ khám phá đặc sản 4 miền & 34 tỉnh thành
        xml.append("  <url>\n");
        xml.append("    <loc>https://vietspecialty.vn/explore</loc>\n");
        xml.append("    <changefreq>weekly</changefreq>\n");
        xml.append("    <priority>0.9</priority>\n");
        xml.append("  </url>\n");

        // Trang danh mục
        List<Category> categories = categoryRepository.findAll();
        for (Category cat : categories) {
            xml.append("  <url>\n");
            xml.append("    <loc>https://vietspecialty.vn/categories/").append(cat.getSlug()).append("</loc>\n");
            xml.append("    <changefreq>weekly</changefreq>\n");
            xml.append("    <priority>0.8</priority>\n");
            xml.append("  </url>\n");
        }

        // Trang sản phẩm đặc sản
        List<Product> products = productRepository.findAll();
        for (Product prod : products) {
            if (prod.getStatus() == ProductStatus.ACTIVE) {
                xml.append("  <url>\n");
                xml.append("    <loc>https://vietspecialty.vn/products/").append(prod.getSlug()).append("</loc>\n");
                xml.append("    <changefreq>weekly</changefreq>\n");
                xml.append("    <priority>0.9</priority>\n");
                xml.append("  </url>\n");
            }
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return "User-agent: *\n" +
                "Allow: /\n" +
                "Disallow: /admin/\n" +
                "Disallow: /seller/\n" +
                "Disallow: /account/\n" +
                "Disallow: /checkout/\n" +
                "Disallow: /api/\n" +
                "Sitemap: https://vietspecialty.vn/sitemap.xml\n";
    }
}

