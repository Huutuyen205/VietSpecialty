package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.OrderItem;
import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.Review;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.domain.enums.ReviewStatus;
import com.vietspecialty.dto.request.ReviewCreateRequest;
import com.vietspecialty.dto.request.SellerReplyRequest;
import com.vietspecialty.dto.response.ReviewResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.mapper.ReviewMapper;
import com.vietspecialty.repository.OrderItemRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.ReviewRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.NotificationService;
import com.vietspecialty.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ReviewResponse createReview(Long userId, ReviewCreateRequest request) {
        OrderItem orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy mặt hàng trong đơn hàng"));

        // 1. Kiểm tra quyền: Người đánh giá phải là người đặt đơn hàng
        if (!orderItem.getOrder().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền đánh giá đơn hàng này");
        }

        // 2. Kiểm tra trạng thái: Đơn hàng phải là DELIVERED
        if (orderItem.getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ELIGIBLE, "Chỉ có thể đánh giá sau khi đã nhận hàng thành công");
        }

        // 3. Chống tự đánh giá sản phẩm của chính shop mình
        Product product = orderItem.getProduct();
        if (product.getShop() != null && product.getShop().getOwner() != null && product.getShop().getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chủ gian hàng không được phép tự đánh giá sản phẩm của mình");
        }

        // 4. Kiểm tra xem đã đánh giá chưa
        if (reviewRepository.existsByOrderItemId(request.orderItemId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mặt hàng này trong đơn đã được bạn đánh giá trước đó");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Review review = Review.builder()
                .orderItem(orderItem)
                .product(product)
                .user(user)
                .rating(request.rating())
                .content(request.content().trim())
                .status(ReviewStatus.VISIBLE)
                .build();

        Review savedReview = reviewRepository.save(review);

        // 5. Tính toán cập nhật avgRating và reviewCount của Product
        updateProductRatingStats(product.getId());

        // 6. Gửi thông báo cho Chủ shop
        if (product.getShop() != null && product.getShop().getOwner() != null) {
            notificationService.createNotification(
                    product.getShop().getOwner(),
                    "REVIEW_NEW",
                    "Đánh giá mới cho " + product.getName(),
                    "Khách hàng đã đánh giá " + request.rating() + " sao: \"" + request.content() + "\"",
                    "/seller/reviews"
            );
        }

        log.info("Review created: id={}, productId={}, rating={}", savedReview.getId(), product.getId(), request.rating());
        return ReviewMapper.toResponse(savedReview);
    }

    @Override
    public Page<ReviewResponse> getProductReviews(Long productId, Integer rating, Pageable pageable) {
        Page<Review> reviewPage;
        if (rating != null && rating >= 1 && rating <= 5) {
            reviewPage = reviewRepository.findByProductIdAndStatusAndRating(productId, ReviewStatus.VISIBLE, rating, pageable);
        } else {
            reviewPage = reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.VISIBLE, pageable);
        }
        return reviewPage.map(ReviewMapper::toResponse);
    }

    @Override
    public Page<ReviewResponse> getSellerShopReviews(Long shopId, Pageable pageable) {
        return reviewRepository.findByProductShopIdOrderByCreatedAtDesc(shopId, pageable)
                .map(ReviewMapper::toResponse);
    }

    @Override
    @Transactional
    public ReviewResponse replyToReview(Long reviewId, Long shopId, SellerReplyRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy đánh giá"));

        if (!review.getProduct().getShop().getId().equals(shopId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Đánh giá không thuộc sản phẩm của gian hàng bạn");
        }

        review.setSellerReply(request.reply().trim());
        review.setSellerRepliedAt(LocalDateTime.now());
        Review updated = reviewRepository.save(review);

        // Gửi thông báo cho người đánh giá
        notificationService.createNotification(
                review.getUser(),
                "REVIEW_REPLIED",
                "Phản hồi từ gian hàng " + review.getProduct().getShop().getName(),
                "Gian hàng đã phản hồi nhận xét của bạn về đặc sản \"" + review.getProduct().getName() + "\"",
                "/products/" + review.getProduct().getSlug()
        );

        return ReviewMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void toggleReviewVisibility(Long reviewId, boolean isVisible) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy đánh giá"));

        review.setStatus(isVisible ? ReviewStatus.VISIBLE : ReviewStatus.HIDDEN);
        reviewRepository.save(review);

        updateProductRatingStats(review.getProduct().getId());
    }

    private void updateProductRatingStats(Long productId) {
        Double avg = reviewRepository.calculateAvgRating(productId);
        Integer count = reviewRepository.countVisibleReviews(productId);

        BigDecimal avgBd = (avg != null && count != null && count > 0)
                ? BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int finalCount = count != null ? count : 0;
        productRepository.updateProductRatingStats(productId, avgBd, finalCount);
        log.info("Updated product rating: productId={}, avgRating={}, count={}", productId, avgBd, finalCount);
    }
}

