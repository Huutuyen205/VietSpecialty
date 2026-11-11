package com.vietspecialty.controller.web;

import com.vietspecialty.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/payment/vnpay")
@RequiredArgsConstructor
public class PaymentController {

    private final VnpayService vnpayService;

    @GetMapping("/return")
    public String vnpayReturn(HttpServletRequest request, Model model) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (String key : requestParams.keySet()) {
            String[] values = requestParams.get(key);
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        }

        String groupCode = params.get("vnp_TxnRef");
        try {
            boolean success = vnpayService.processReturn(params);
            if (success) {
                return "redirect:/checkout/success?groupCode=" + groupCode;
            } else {
                model.addAttribute("groupCode", groupCode);
                model.addAttribute("responseCode", params.get("vnp_ResponseCode"));
                return "web/payment-failed";
            }
        } catch (Exception e) {
            log.error("Error processing VNPay return", e);
            model.addAttribute("groupCode", groupCode);
            model.addAttribute("errorMessage", e.getMessage());
            return "web/payment-failed";
        }
    }
}

