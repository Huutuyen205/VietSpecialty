package com.vietspecialty.service;

import com.vietspecialty.domain.entity.OrderGroup;
import com.vietspecialty.domain.enums.ActorType;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.dto.response.OrderDetailResponse;
import com.vietspecialty.dto.response.OrderGroupSummaryResponse;
import com.vietspecialty.dto.response.OrderSummaryResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface OrderService {

    CheckoutPreviewResponse previewCheckout(Long userId, String shippingMethod, String systemVoucherCode, Map<Long, String> shopVoucherCodes);

    OrderGroup placeOrder(Long userId, CheckoutFormRequest request, String clientIp);

    OrderGroupSummaryResponse getOrderGroupSummary(String groupCode, Long userId);

    OrderDetailResponse getOrderDetail(String orderCode, Long userId);

    OrderDetailResponse getSellerOrderDetail(String orderCode, Long shopId);

    Page<OrderSummaryResponse> getBuyerOrders(Long userId, OrderStatus status, Pageable pageable);

    void cancelOrder(Long orderId, Long userId, String reason);

    void confirmOrderReceived(Long orderId, Long userId);

    Page<OrderSummaryResponse> getSellerOrders(Long shopId, OrderStatus status, String search, Pageable pageable);

    void confirmSellerOrder(Long orderId, Long shopId);

    void shipSellerOrder(Long orderId, Long shopId, String carrierName, String trackingCode);

    void cancelSellerOrder(Long orderId, Long shopId, String reason);

    void changeOrderStatus(Long orderId, OrderStatus newStatus, ActorType actorType, Long actorId, String note, String cancelReason);

    void expireUnpaidOrderGroups();

    void autoDeliverOrders();
}
