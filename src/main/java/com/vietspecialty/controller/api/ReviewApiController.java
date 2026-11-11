package com.vietspecialty.controller.api;

import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.request.ReviewCreateRequest;
import com.vietspecialty.dto.request.SellerReplyRequest;
import com.vietspecialty.dto.response.ReviewResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.ReviewService;
import com.vietspecialty.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewApiController {

    private final ReviewService reviewService;
    private final ShopService shopService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ReviewResponse response = reviewService.createReview(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<ReviewResponse> response = reviewService.getProductReviews(productId, rating, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> replyToReview(
            @PathVariable Long id,
            @CurrentUser CustomUserDetails userDetails,
            @Valid @RequestBody SellerReplyRequest request
    ) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        ReviewResponse response = reviewService.replyToReview(id, shop.id(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

