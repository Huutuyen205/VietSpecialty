package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.entity.ProductComment;
import com.vietspecialty.domain.entity.Review;
import com.vietspecialty.domain.enums.CommentStatus;
import com.vietspecialty.domain.enums.ReviewStatus;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.ReviewRepository;
import com.vietspecialty.service.ProductCommentService;
import com.vietspecialty.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/moderation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {

    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final ProductCommentService productCommentService;

    @GetMapping
    public String moderationPage(
            @RequestParam(value = "tab", defaultValue = "reviews") String tab,
            @RequestParam(value = "reviewPage", defaultValue = "0") int reviewPage,
            @RequestParam(value = "commentPage", defaultValue = "0") int commentPage,
            @RequestParam(value = "size", defaultValue = "12") int size,
            Model model
    ) {
        Page<Review> reviews = reviewRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, reviewPage), Math.max(1, size))
        );
        Page<ProductComment> comments = productCommentService.getAllCommentsForAdmin(
                null, PageRequest.of(Math.max(0, commentPage), Math.max(1, size))
        );

        model.addAttribute("reviews", reviews);
        model.addAttribute("comments", comments);
        model.addAttribute("activeTab", tab);
        model.addAttribute("activeNav", "moderation");

        return "admin/moderation/index";
    }

    @PostMapping({"/reviews/{id}/toggle", "/reviews/{id}/status"})
    public String toggleReview(
            @PathVariable("id") Long reviewId,
            @RequestParam(value = "visible", required = false) Boolean visible,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Review review = reviewRepository.findById(reviewId).orElse(null);
            if (review == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy đánh giá");
                return "redirect:/admin/moderation?tab=reviews";
            }
            boolean targetVisible = (visible != null)
                    ? visible
                    : (review.getStatus() != ReviewStatus.VISIBLE);

            reviewService.toggleReviewVisibility(reviewId, targetVisible);
            redirectAttributes.addFlashAttribute("successMessage",
                    targetVisible
                            ? "Đã hiển thị lại đánh giá và tính lại điểm sao trung bình sản phẩm!"
                            : "Đã ẩn đánh giá vi phạm và tính lại điểm sao trung bình sản phẩm!");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/moderation?tab=reviews";
    }

    @PostMapping("/reviews/{id}/hide")
    public String hideReview(@PathVariable("id") Long reviewId, RedirectAttributes redirectAttributes) {
        return toggleReview(reviewId, false, redirectAttributes);
    }

    @PostMapping("/reviews/{id}/show")
    public String showReview(@PathVariable("id") Long reviewId, RedirectAttributes redirectAttributes) {
        return toggleReview(reviewId, true, redirectAttributes);
    }

    @PostMapping({"/comments/{id}/toggle", "/comments/{id}/status"})
    public String toggleComment(
            @PathVariable("id") Long commentId,
            @RequestParam(value = "visible", required = false) Boolean visible,
            RedirectAttributes redirectAttributes
    ) {
        try {
            productCommentService.toggleCommentVisibility(commentId, visible);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái hiển thị câu hỏi/bình luận Q&A!");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/moderation?tab=comments";
    }

    @PostMapping("/comments/{id}/hide")
    public String hideComment(@PathVariable("id") Long commentId, RedirectAttributes redirectAttributes) {
        return toggleComment(commentId, false, redirectAttributes);
    }

    @PostMapping("/comments/{id}/show")
    public String showComment(@PathVariable("id") Long commentId, RedirectAttributes redirectAttributes) {
        return toggleComment(commentId, true, redirectAttributes);
    }
}
