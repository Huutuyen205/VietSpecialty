package com.vietspecialty.service;

import com.vietspecialty.domain.entity.ProductComment;
import com.vietspecialty.domain.enums.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductCommentService {

    Page<ProductComment> getProductQuestions(Long productId, Pageable pageable);

    Page<ProductComment> getShopQuestions(Long shopId, Pageable pageable);

    Page<ProductComment> getAllCommentsForAdmin(CommentStatus status, Pageable pageable);

    ProductComment askQuestion(Long productId, Long userId, String content);

    ProductComment replyToQuestion(Long commentId, Long shopId, Long sellerUserId, String replyContent);

    void toggleCommentVisibility(Long commentId, Boolean isVisible);
}
