package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.Wishlist;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.mapper.ProductMapper;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.repository.WishlistRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WishlistController {

    private final ProductRepository productRepository;
    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;

    private static final String SESSION_WISHLIST_KEY = "VS_WISHLIST_IDS";

    @GetMapping({"/wishlist", "/account/wishlist"})
    @Transactional
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
    @Transactional
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
    @Transactional
    public Map<String, Object> toggleWishlist(
            @PathVariable Long productId,
            @CurrentUser CustomUserDetails userDetails,
            HttpSession session
    ) {
        if (!productRepository.existsById(productId)) {
            return Map.of("success", false, "message", "Sản phẩm không tồn tại");
        }

        Set<Long> ids;
        boolean added;
        if (userDetails != null) {
            Long userId = userDetails.getId();
            syncSessionToDb(userId, session);
            if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
                wishlistRepository.deleteByUserIdAndProductId(userId, productId);
                added = false;
            } else {
                wishlistRepository.save(Wishlist.builder()
                        .user(userRepository.getReferenceById(userId))
                        .product(productRepository.getReferenceById(productId))
                        .build());
                added = true;
            }
            ids = new LinkedHashSet<>(wishlistRepository.findProductIdsByUserId(userId));
            session.setAttribute(SESSION_WISHLIST_KEY, new LinkedHashSet<>(ids));
        } else {
            ids = getSessionWishlistIds(session);
            if (ids.contains(productId)) {
                ids.remove(productId);
                added = false;
            } else {
                ids.add(productId);
                added = true;
            }
            session.setAttribute(SESSION_WISHLIST_KEY, new LinkedHashSet<>(ids));
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("added", added);
        res.put("count", ids.size());
        res.put("message", added ? "Đã thêm đặc sản vào danh sách yêu thích" : "Đã bỏ khỏi danh sách yêu thích");
        return res;
    }

    @PostMapping("/wishlist/clear")
    @Transactional
    public String clearWishlist(
            @CurrentUser CustomUserDetails userDetails,
            HttpSession session
    ) {
        if (userDetails != null) {
            wishlistRepository.deleteAllByUserId(userDetails.getId());
        }
        session.setAttribute(SESSION_WISHLIST_KEY, new LinkedHashSet<Long>());
        return "redirect:/wishlist";
    }

    @SuppressWarnings("unchecked")
    private Set<Long> getSessionWishlistIds(HttpSession session) {
        Set<Long> sessionIds = (Set<Long>) session.getAttribute(SESSION_WISHLIST_KEY);
        if (sessionIds == null) {
            sessionIds = new LinkedHashSet<>();
            session.setAttribute(SESSION_WISHLIST_KEY, sessionIds);
        }
        return sessionIds;
    }

    private void syncSessionToDb(Long userId, HttpSession session) {
        Set<Long> sessionIds = getSessionWishlistIds(session);
        if (sessionIds.isEmpty()) {
            return;
        }
        for (Long pid : sessionIds) {
            if (pid != null
                    && productRepository.existsById(pid)
                    && !wishlistRepository.existsByUserIdAndProductId(userId, pid)) {
                wishlistRepository.save(Wishlist.builder()
                        .user(userRepository.getReferenceById(userId))
                        .product(productRepository.getReferenceById(pid))
                        .build());
            }
        }
    }

    private Set<Long> getWishlistIds(CustomUserDetails userDetails, HttpSession session) {
        if (userDetails != null) {
            Long userId = userDetails.getId();
            syncSessionToDb(userId, session);
            Set<Long> dbIds = new LinkedHashSet<>(wishlistRepository.findProductIdsByUserId(userId));
            session.setAttribute(SESSION_WISHLIST_KEY, new LinkedHashSet<>(dbIds));
            return dbIds;
        }
        return getSessionWishlistIds(session);
    }

    private List<ProductCardResponse> getProductsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findActiveWithDetailsByIds(ids);
        Map<Long, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProductMapper::toCardResponse)
                .collect(Collectors.toList());
    }
}
