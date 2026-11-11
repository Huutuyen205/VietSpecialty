package com.vietspecialty.service;

import com.vietspecialty.domain.entity.OrderGroup;

import java.util.Map;

public interface VnpayService {

    String createPaymentUrl(OrderGroup orderGroup, String clientIp);

    boolean processReturn(Map<String, String> params);

    Map<String, String> processIpn(Map<String, String> params);
}

