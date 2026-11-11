package com.vietspecialty.service;

import com.vietspecialty.dto.request.ReviewCreateRequest;
import com.vietspecialty.dto.request.SellerReplyRequest;
import com.vietspecialty.dto.response.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ReviewResponse createReview(Long userId, ReviewCreateRequest request);

    Page<ReviewResponse> getProductReviews(Long productId, Integer rating, Pageable pageable);

    Page<ReviewResponse> getSellerShopReviews(Long shopId, Pageable pageable);

    ReviewResponse replyToReview(Long reviewId, Long shopId, SellerReplyRequest request);

    void toggleReviewVisibility(Long reviewId, boolean isVisible);
}

