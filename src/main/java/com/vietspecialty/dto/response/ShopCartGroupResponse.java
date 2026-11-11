package com.vietspecialty.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ShopCartGroupResponse(
        Long shopId,
        String shopName,
        String shopSlug,
        String provinceName,
        List<CartItemResponse> items,
        BigDecimal groupTotal,
        String groupTotalFormatted
) {}

