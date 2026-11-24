package com.vietspecialty.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopPackageGiftRequest {
    private Boolean isGift;
    private String giftOccasion;
    private String giftWrapType;
    private String giftRecipientName;
    private String giftMessage;
}
