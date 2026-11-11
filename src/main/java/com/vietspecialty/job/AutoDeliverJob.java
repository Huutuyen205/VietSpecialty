package com.vietspecialty.job;

import com.vietspecialty.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDeliverJob {

    private final OrderService orderService;

    /**
     * Tự động hoàn tất giao hàng sau 7 ngày vận chuyển.
     * Chạy định kỳ vào 01:00 AM mỗi ngày.
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void run() {
        try {
            orderService.autoDeliverOrders();
        } catch (Exception e) {
            log.error("Error running AutoDeliverJob", e);
        }
    }
}

