package com.vietspecialty.dto.response;

import java.math.BigDecimal;

public record RevenueStatResponse(
        String label,       // e.g. "05/11", "11/2026"
        BigDecimal revenue, // e.g. 15000000
        Long orderCount     // e.g. 12
) {}

