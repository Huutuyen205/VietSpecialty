package com.vietspecialty.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartSummaryResponse(
        List<ShopCartGroupResponse> shopGroups,
        int totalItemCount,
        BigDecimal grandTotal,
        String grandTotalFormatted
) {}

