package com.vietspecialty.controller.web;

import com.vietspecialty.dto.response.CartSummaryResponse;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/cart")
    public String viewCart(@CurrentUser CustomUserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/cart";
        }

        CartSummaryResponse cart = cartService.getCart(userDetails.getId());
        model.addAttribute("cart", cart);

        return "web/cart";
    }
}

