package com.vietspecialty.service;

import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.response.CartSummaryResponse;

public interface CartService {

    CartSummaryResponse getCart(Long userId);

    CartSummaryResponse addToCart(Long userId, CartItemRequest request);

    CartSummaryResponse updateQuantity(Long userId, Long cartItemId, Integer quantity);

    CartSummaryResponse removeFromCart(Long userId, Long cartItemId);

    void clearCart(Long userId);

    int getCartItemCount(Long userId);
}

