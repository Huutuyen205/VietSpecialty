package com.vietspecialty.controller.api;

import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderService orderService;

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @CurrentUser CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String reason = body != null ? body.get("reason") : "Người mua đổi ý";
        orderService.cancelOrder(id, userDetails.getId(), reason);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/received")
    public ResponseEntity<ApiResponse<Void>> confirmOrderReceived(
            @PathVariable Long id,
            @CurrentUser CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        orderService.confirmOrderReceived(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
