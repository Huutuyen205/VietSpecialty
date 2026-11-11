package com.vietspecialty.controller.api;

import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.response.CartSummaryResponse;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartApiController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartSummaryResponse>> getCart(@CurrentUser CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.getDefaultMessage()));
        }
        return ResponseEntity.ok(ApiResponse.success(cartService.getCart(userDetails.getId())));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartSummaryResponse>> addToCart(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @RequestBody CartItemRequest request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.getDefaultMessage()));
        }
        CartSummaryResponse summary = cartService.addToCart(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm đặc sản vào giỏ hàng", summary));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartSummaryResponse>> updateQuantity(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.getDefaultMessage()));
        }
        Integer quantity = body.get("quantity");
        CartSummaryResponse summary = cartService.updateQuantity(userDetails.getId(), id, quantity);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật số lượng", summary));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartSummaryResponse>> removeFromCart(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.getDefaultMessage()));
        }
        CartSummaryResponse summary = cartService.removeFromCart(userDetails.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa đặc sản khỏi giỏ hàng", summary));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Integer>> getCartCount(@CurrentUser CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(0));
        }
        return ResponseEntity.ok(ApiResponse.success(cartService.getCartItemCount(userDetails.getId())));
    }
}
