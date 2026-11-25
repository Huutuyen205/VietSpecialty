package com.vietspecialty.service.impl;

import com.vietspecialty.config.AppProperties;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.OrderGroupRepository;
import com.vietspecialty.repository.OrderRepository;
import com.vietspecialty.repository.OrderStatusHistoryRepository;
import com.vietspecialty.repository.PaymentRepository;
import com.vietspecialty.service.VnpayService;
import com.vietspecialty.util.VnpaySigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayServiceImpl implements VnpayService {

    private final AppProperties appProperties;
    private final OrderGroupRepository orderGroupRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Override
    public String createPaymentUrl(OrderGroup orderGroup, String clientIp) {
        AppProperties.Vnpay vnpay = appProperties.getVnpay();

        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";
        if (ip.equals("0:0:0:0:0:0:0:1") || ip.contains(",")) {
            ip = "127.0.0.1";
        }

        long amount = orderGroup.getGrandTotal().multiply(BigDecimal.valueOf(100)).longValue();

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String vnpCreateDate = formatter.format(cld.getTime());

        // Hạn thanh toán
        cld.add(Calendar.MINUTE, appProperties.getOrder().getPaymentTimeoutMinutes());
        String vnpExpireDate = formatter.format(cld.getTime());

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpay.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(amount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", orderGroup.getCode());
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang VietSpecialty " + orderGroup.getCode());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnpay.getReturnUrl());
        vnpParams.put("vnp_IpAddr", ip);
        vnpParams.put("vnp_CreateDate", vnpCreateDate);
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        String hashData = VnpaySigner.buildHashData(vnpParams);
        String vnpSecureHash = VnpaySigner.hmacSHA512(vnpay.getHashSecret(), hashData);
        String queryUrl = VnpaySigner.buildQueryString(vnpParams) + "&vnp_SecureHash=" + vnpSecureHash;

        String paymentUrl = vnpay.getPayUrl() + "?" + queryUrl;
        log.info("Generated VNPay payment URL for groupCode={}", orderGroup.getCode());

        return paymentUrl;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public boolean processReturn(Map<String, String> params) {
        log.info("Processing VNPay Return URL callback: {}", params);
        return handleVnpayCallback(params, false);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public Map<String, String> processIpn(Map<String, String> params) {
        log.info("Processing VNPay IPN webhook: {}", params);
        Map<String, String> response = new HashMap<>();

        try {
            handleVnpayCallback(params, true);
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
        } catch (BusinessException e) {
            ErrorCode errorCode = e.getErrorCode();
            if (errorCode == ErrorCode.PAYMENT_INVALID_SIGNATURE) {
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
            } else if (errorCode == ErrorCode.ORDER_GROUP_NOT_FOUND
                    || errorCode == ErrorCode.ORDER_NOT_FOUND
                    || errorCode == ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND) {
                response.put("RspCode", "01");
                response.put("Message", "Order not found");
            } else if (errorCode == ErrorCode.PAYMENT_AMOUNT_MISMATCH) {
                response.put("RspCode", "04");
                response.put("Message", "Invalid amount");
            } else if (errorCode == ErrorCode.PAYMENT_ALREADY_PROCESSED) {
                response.put("RspCode", "02");
                response.put("Message", "Order already confirmed");
            } else {
                response.put("RspCode", "99");
                response.put("Message", "Unknown error");
            }
        } catch (Exception e) {
            log.error("Unhandled error processing VNPay IPN", e);
            response.put("RspCode", "99");
            response.put("Message", "Unknown error");
        }

        return response;
    }

    private boolean handleVnpayCallback(Map<String, String> params, boolean isIpn) {
        String vnpSecureHash = params.get("vnp_SecureHash");
        if (vnpSecureHash == null || vnpSecureHash.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_SIGNATURE);
        }

        // Tách vnp_SecureHash ra để tạo hash data kiểm tra
        Map<String, String> verifyParams = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key)) {
                verifyParams.put(key, entry.getValue());
            }
        }

        String hashData = VnpaySigner.buildHashData(verifyParams);
        String calculatedSignature = VnpaySigner.hmacSHA512(appProperties.getVnpay().getHashSecret(), hashData);

        if (!calculatedSignature.equalsIgnoreCase(vnpSecureHash)) {
            log.error("VNPay signature mismatch: received={}, calculated={}", vnpSecureHash, calculatedSignature);
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_SIGNATURE);
        }

        String groupCode = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        String payDate = params.get("vnp_PayDate");

        if (groupCode == null || groupCode.isBlank()) {
            throw new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND);
        }

        OrderGroup orderGroup = orderGroupRepository.findByCode(groupCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));

        long expectedAmount = orderGroup.getGrandTotal().multiply(BigDecimal.valueOf(100)).longValue();
        String vnpAmountStr = params.get("vnp_Amount");
        if (isIpn || "00".equals(responseCode)) {
            if (vnpAmountStr == null || vnpAmountStr.isBlank()) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }
            try {
                long receivedAmount = Long.parseLong(vnpAmountStr.trim());
                if (receivedAmount != expectedAmount) {
                    throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
                }
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }
        } else if (vnpAmountStr != null && !vnpAmountStr.isBlank()) {
            try {
                long receivedAmount = Long.parseLong(vnpAmountStr.trim());
                if (receivedAmount != expectedAmount) {
                    throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
                }
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }
        }

        Payment payment = paymentRepository.findTopByOrderGroupIdOrderByCreatedAtDesc(orderGroup.getId())
                .orElse(null);

        // Idempotent: Nếu đã thành công trước đó thì không xử lý lại
        if (orderGroup.getPaymentStatus() == OrderGroupPaymentStatus.PAID) {
            log.info("OrderGroup {} already paid, skipping duplicate update", groupCode);
            if (isIpn) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
            }
            return true;
        }

        boolean isPaymentSuccess = "00".equals(responseCode);

        if (payment != null) {
            payment.setVnpResponseCode(responseCode);
            payment.setVnpTransactionNo(transactionNo);
            payment.setVnpBankCode(bankCode);
            payment.setVnpPayDate(payDate);
            payment.setRawCallback(params.toString());
            payment.setStatus(isPaymentSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
            if (isPaymentSuccess) {
                payment.setPaidAt(LocalDateTime.now());
            }
            paymentRepository.save(payment);
        }

        if (isPaymentSuccess) {
            orderGroup.setPaymentStatus(OrderGroupPaymentStatus.PAID);
            orderGroupRepository.save(orderGroup);

            // Cập nhật từng đơn hàng con sang PENDING_CONFIRM
            List<Order> orders = orderRepository.findByOrderGroupId(orderGroup.getId());
            for (Order order : orders) {
                order.setPaymentStatus(OrderPaymentStatus.PAID);
                order.setStatus(OrderStatus.PENDING_CONFIRM);
                orderRepository.save(order);

                OrderStatusHistory history = OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(OrderStatus.PENDING_PAYMENT)
                        .toStatus(OrderStatus.PENDING_CONFIRM)
                        .actorType(ActorType.SYSTEM)
                        .note("Thanh toán thành công qua cổng VNPay (Mã GD: " + transactionNo + ")")
                        .build();
                orderStatusHistoryRepository.save(history);
            }

            log.info("Successfully confirmed payment for orderGroup: {}, total orders: {}", groupCode, orders.size());
            return true;
        } else {
            orderGroup.setPaymentStatus(OrderGroupPaymentStatus.FAILED);
            orderGroupRepository.save(orderGroup);
            log.warn("Payment failed for orderGroup: {}, responseCode={}", groupCode, responseCode);
            return false;
        }
    }
}

