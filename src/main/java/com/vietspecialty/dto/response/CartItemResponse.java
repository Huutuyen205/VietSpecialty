package com.vietspecialty.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        String productSlug,
        String primaryImageUrl,
        BigDecimal price,
        String priceFormatted,
        String unit,
        Integer quantity,
        BigDecimal subtotal,
        String subtotalFormatted,
        Integer stock,
        boolean isOutOfStock
) {}

