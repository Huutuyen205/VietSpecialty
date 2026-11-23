package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.ProductComment;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.CommentStatus;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.ProductCommentRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.NotificationService;
import com.vietspecialty.service.ProductCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCommentServiceImpl implements ProductCommentService {

    private final ProductCommentRepository productCommentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    public Page<ProductComment> getProductQuestions(Long productId, Pageable pageable) {
        return productCommentRepository.findByProductIdAndParentIsNullAndStatusOrderByCreatedAtDesc(
                productId, CommentStatus.VISIBLE, pageable
        );
    }

    @Override
    public Page<ProductComment> getShopQuestions(Long shopId, Pageable pageable) {
        return productCommentRepository.findByProductShopIdAndParentIsNullOrderByCreatedAtDesc(shopId, pageable);
    }

    @Override
    public Page<ProductComment> getAllCommentsForAdmin(CommentStatus status, Pageable pageable) {
        if (status != null) {
            return productCommentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return productCommentRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Override
    @Transactional
    public ProductComment askQuestion(Long productId, Long userId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nội dung câu hỏi không được để trống");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ProductComment question = ProductComment.builder()
                .product(product)
                .user(user)
                .parent(null)
                .content(content.trim())
                .isSellerReply(false)
                .status(CommentStatus.VISIBLE)
                .build();

        ProductComment saved = productCommentRepository.save(question);

        if (product.getShop() != null && product.getShop().getOwner() != null
                && !product.getShop().getOwner().getId().equals(userId)) {
            notificationService.createNotification(
                    product.getShop().getOwner(),
                    "QUESTION_NEW",
                    "Câu hỏi mới về " + product.getName(),
                    user.getFullName() + " vừa đặt câu hỏi: \"" + content.trim() + "\"",
                    "/seller/questions"
            );
        }

        log.info("Created product question id={} for productId={} by userId={}", saved.getId(), productId, userId);
        return saved;
    }

    @Override
    @Transactional
    public ProductComment replyToQuestion(Long commentId, Long shopId, Long sellerUserId, String replyContent) {
        if (replyContent == null || replyContent.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nội dung trả lời không được để trống");
        }

        ProductComment question = productCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy câu hỏi"));

        Product product = question.getProduct();
        if (product == null || product.getShop() == null || !product.getShop().getId().equals(shopId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Câu hỏi này không thuộc sản phẩm của gian hàng bạn");
        }

        User seller = userRepository.findById(sellerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ProductComment parentComment = question.getParent() != null ? question.getParent() : question;

        ProductComment reply = ProductComment.builder()
                .product(product)
                .user(seller)
                .parent(parentComment)
                .content(replyContent.trim())
                .isSellerReply(true)
                .status(CommentStatus.VISIBLE)
                .build();

        ProductComment savedReply = productCommentRepository.save(reply);
        parentComment.getReplies().add(savedReply);

        if (parentComment.getUser() != null && !parentComment.getUser().getId().equals(sellerUserId)) {
            notificationService.createNotification(
                    parentComment.getUser(),
                    "QUESTION_REPLIED",
                    "Gian hàng " + product.getShop().getName() + " đã trả lời câu hỏi của bạn",
                    "Phản hồi về \"" + product.getName() + "\": " + replyContent.trim(),
                    "/products/" + product.getSlug()
            );
        }

        log.info("Seller replied to question id={} with replyId={} for shopId={}", parentComment.getId(), savedReply.getId(), shopId);
        return savedReply;
    }

    @Override
    @Transactional
    public void toggleCommentVisibility(Long commentId, Boolean isVisible) {
        ProductComment comment = productCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy bình luận hỏi đáp"));

        if (isVisible != null) {
            comment.setStatus(isVisible ? CommentStatus.VISIBLE : CommentStatus.HIDDEN);
        } else {
            comment.setStatus(comment.getStatus() == CommentStatus.VISIBLE ? CommentStatus.HIDDEN : CommentStatus.VISIBLE);
        }
        productCommentRepository.save(comment);
        log.info("Toggled comment id={} visibility to {}", commentId, comment.getStatus());
    }
}
