package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.ProductComment;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.ProductCommentService;
import com.vietspecialty.service.ShopService;
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
@RequestMapping("/seller/questions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerQuestionController {

    private final ProductCommentService productCommentService;
    private final ShopService shopService;

    @GetMapping
    public String listQuestions(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<ProductComment> questionPage = productCommentService.getShopQuestions(shop.id(), pageable);

        model.addAttribute("shop", shop);
        model.addAttribute("questionPage", questionPage);
        model.addAttribute("activeNav", "questions");

        return "seller/questions/index";
    }

    @PostMapping("/{id}/reply")
    public String replyQuestion(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable("id") Long commentId,
            @RequestParam(value = "reply", required = false) String reply,
            @RequestParam(value = "content", required = false) String content,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        String answer = (reply != null && !reply.isBlank()) ? reply : content;
        try {
            productCommentService.replyToQuestion(commentId, shop.id(), userDetails.getId(), answer);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi câu trả lời cho khách hàng thành công!");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/seller/questions";
    }
}
