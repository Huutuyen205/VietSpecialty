package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.mapper.ProductMapper;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WishlistController {

    private final ProductRepository productRepository;

    // Lưu wishlist theo userId trong bộ nhớ (đồng bộ cùng session cho khách/user)
    private static final Map<Long, Set<Long>> USER_WISHLISTS = new ConcurrentHashMap<>();
    private static final String SESSION_WISHLIST_KEY = "VS_WISHLIST_IDS";

    @GetMapping({"/wishlist", "/account/wishlist"})
    public String wishlistPage(
            @CurrentUser CustomUserDetails userDetails,
            HttpSession session,
            @RequestParam(required = false) String ids,
            Model model
    ) {
        Set<Long> productIds = getWishlistIds(userDetails, session);

        if (ids != null && !ids.isBlank()) {
            Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> s.matches("\\d+"))
                    .map(Long::parseLong)
                    .forEach(productIds::add);
        }

        List<ProductCardResponse> wishlistProducts = getProductsByIds(productIds);
        model.addAttribute("wishlistProducts", wishlistProducts);
        model.addAttribute("wishlistIds", productIds);
        model.addAttribute("pageTitle", "Đặc sản yêu thích — VietSpecialty");

        return "web/wishlist";
    }

    @PostMapping({"/wishlist/toggle", "/api/wishlist/toggle"})
    @ResponseBody
    public Map<String, Object> toggleWishlistBody(
            @RequestParam(required = false) Long productId,
            @RequestBody(required = false) Map<String, Object> body,
            @CurrentUser CustomUserDetails userDetails,
            HttpSession session
    ) {
        Long pid = productId;
        if (pid == null && body != null && body.get("productId") != null) {
            try {
                pid = Long.valueOf(body.get("productId").toString());
            } catch (NumberFormatException ignored) {}
        }
        if (pid == null) {
            return Map.of("success", false, "message", "Thiếu productId");
        }
        return toggleWishlist(pid, userDetails, session);
    }

    @PostMapping({"/wishlist/toggle/{productId}", "/account/wishlist/toggle/{productId}", "/api/wishlist/toggle/{productId}"})
    @ResponseBody
    public Map<String, Object> toggleWishlist(
            @PathVariable Long productId,
            @CurrentUser CustomUserDetails userDetails,
            HttpSession session
    ) {
        Set<Long> ids = getWishlistIds(userDetails, session);
        boolean added;
        if (ids.contains(productId)) {
            ids.remove(productId);
            added = false;
        } else {
            ids.add(productId);
            added = true;
        }
        saveWishlistIds(userDetails, session, ids);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("added", added);
        res.put("count", ids.size());
        res.put("message", added ? "Đã thêm đặc sản vào danh sách yêu thích" : "Đã bỏ khỏi danh sách yêu thích");
        return res;
    }

    @PostMapping("/wishlist/clear")
    public String clearWishlist(
            @CurrentUser CustomUserDetails userDetails,
            HttpSession session
    ) {
        Set<Long> ids = getWishlistIds(userDetails, session);
        ids.clear();
        saveWishlistIds(userDetails, session, ids);
        return "redirect:/wishlist";
    }

    @SuppressWarnings("unchecked")
    private Set<Long> getWishlistIds(CustomUserDetails userDetails, HttpSession session) {
        Set<Long> sessionIds = (Set<Long>) session.getAttribute(SESSION_WISHLIST_KEY);
        if (sessionIds == null) {
            sessionIds = new LinkedHashSet<>();
            session.setAttribute(SESSION_WISHLIST_KEY, sessionIds);
        }

        if (userDetails != null) {
            Set<Long> userIds = USER_WISHLISTS.computeIfAbsent(userDetails.getId(), k -> ConcurrentHashMap.newKeySet());
            userIds.addAll(sessionIds);
            sessionIds.addAll(userIds);
            return new LinkedHashSet<>(userIds);
        }

        return sessionIds;
    }

    private void saveWishlistIds(CustomUserDetails userDetails, HttpSession session, Set<Long> ids) {
        session.setAttribute(SESSION_WISHLIST_KEY, new LinkedHashSet<>(ids));
        if (userDetails != null) {
            Set<Long> userIds = USER_WISHLISTS.computeIfAbsent(userDetails.getId(), k -> ConcurrentHashMap.newKeySet());
            userIds.clear();
            userIds.addAll(ids);
        }
    }

    private List<ProductCardResponse> getProductsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findAllById(ids);
        return products.stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .map(ProductMapper::toCardResponse)
                .collect(Collectors.toList());
    }
}
