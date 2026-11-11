package com.vietspecialty.controller.web;

import com.vietspecialty.dto.request.SellerReplyRequest;
import com.vietspecialty.dto.response.ReviewResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.ReviewService;
import com.vietspecialty.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/seller/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerReviewController {

    private final ReviewService reviewService;
    private final ShopService shopService;

    @GetMapping
    public String listReviews(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<ReviewResponse> reviewPage = reviewService.getSellerShopReviews(shop.id(), pageable);

        model.addAttribute("shop", shop);
        model.addAttribute("reviewPage", reviewPage);
        model.addAttribute("activeNav", "reviews");

        return "seller/reviews/index";
    }

    @PostMapping("/{id}/reply")
    public String replyReview(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @ModelAttribute SellerReplyRequest request,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        try {
            reviewService.replyToReview(id, shop.id(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi phản hồi đánh giá của khách hàng.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/seller/reviews";
    }
}

