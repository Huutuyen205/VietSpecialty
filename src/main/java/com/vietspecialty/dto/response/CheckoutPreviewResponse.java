package com.vietspecialty.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewResponse(
        AddressResponse selectedAddress,
        List<ShopCheckoutGroupResponse> shopGroups,
        BigDecimal subtotal,
        String subtotalFormatted,
        BigDecimal totalShippingFee,
        String totalShippingFeeFormatted,
        BigDecimal systemDiscount,
        String systemDiscountFormatted,
        BigDecimal totalShopDiscount,
        String totalShopDiscountFormatted,
        BigDecimal grandTotal,
        String grandTotalFormatted,
        String checkoutToken
) {
    public record ShopCheckoutGroupResponse(
            Long shopId,
            String shopName,
            String provinceName,
            List<CartItemResponse> items,
            BigDecimal goodsTotal,
            String goodsTotalFormatted,
            BigDecimal shippingFee,
            String shippingFeeFormatted,
            BigDecimal shopDiscount,
            String shopDiscountFormatted,
            BigDecimal groupTotal,
            String groupTotalFormatted
    ) {}
}

