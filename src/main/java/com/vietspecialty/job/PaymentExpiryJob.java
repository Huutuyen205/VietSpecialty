package com.vietspecialty.job;

import com.vietspecialty.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryJob {

    private final OrderService orderService;

    /**
     * Quét và hủy các đơn hàng VNPay quá 15 phút chưa thanh toán.
     * Chạy định kỳ mỗi phút một lần.
     */
    @Scheduled(cron = "0 */1 * * * *")
    public void run() {
        try {
            orderService.expireUnpaidOrderGroups();
        } catch (Exception e) {
            log.error("Error running PaymentExpiryJob", e);
        }
    }
}

