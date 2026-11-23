package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.OrderGroup;
import com.vietspecialty.domain.enums.PaymentMethod;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.AddressResponse;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.dto.response.OrderGroupSummaryResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.AddressService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.VnpayService;
import com.vietspecialty.service.VoucherService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CheckoutController {

    private final OrderService orderService;
    private final AddressService addressService;
    private final VoucherService voucherService;
    private final VnpayService vnpayService;

    @GetMapping("/checkout")
    public String checkoutPage(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "STANDARD") String shippingMethod,
            @RequestParam(required = false) String systemVoucherCode,
            @RequestParam Map<String, String> allParams,
            Model model
    ) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/checkout";
        }

        try {
            Map<Long, String> shopVoucherCodes = new HashMap<>();
            if (allParams != null) {
                for (Map.Entry<String, String> entry : allParams.entrySet()) {
                    String key = entry.getKey();
                    String val = entry.getValue();
                    if (key != null && key.startsWith("shopVoucher_") && val != null && !val.isBlank()) {
                        try {
                            Long shopId = Long.parseLong(key.substring("shopVoucher_".length()));
                            shopVoucherCodes.put(shopId, val.trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (systemVoucherCode != null && !systemVoucherCode.isBlank()) {
                try {
                    // Kiểm tra sơ bộ để hiển thị thông báo nếu mã sàn không hợp lệ
                    CheckoutPreviewResponse tempPreview = orderService.previewCheckout(userDetails.getId(), shippingMethod, null, shopVoucherCodes);
                    voucherService.validateAndGetVoucher(systemVoucherCode.trim(), null, tempPreview.subtotal(), userDetails.getId());
                    model.addAttribute("voucherSuccessMessage", "Đã áp dụng mã ưu đãi sàn: " + systemVoucherCode.trim().toUpperCase());
                } catch (BusinessException vex) {
                    model.addAttribute("voucherErrorMessage", vex.getMessage());
                }
            }

            CheckoutPreviewResponse preview = orderService.previewCheckout(
                    userDetails.getId(), shippingMethod, systemVoucherCode, shopVoucherCodes
            );

            List<AddressResponse> userAddresses = addressService.getUserAddresses(userDetails.getId());

            model.addAttribute("preview", preview);
            model.addAttribute("addresses", userAddresses);
            model.addAttribute("systemVouchers", voucherService.getAvailableSystemVouchers());
            model.addAttribute("selectedShippingMethod", shippingMethod);
            model.addAttribute("selectedSystemVoucher", systemVoucherCode);
            model.addAttribute("selectedShopVouchers", shopVoucherCodes);

            return "web/checkout";
        } catch (BusinessException e) {
            log.warn("Cannot checkout: {}", e.getMessage());
            return "redirect:/cart";
        }
    }

    @PostMapping("/checkout/place-order")
    public String placeOrder(
            @CurrentUser CustomUserDetails userDetails,
            @Valid @ModelAttribute CheckoutFormRequest request,
            BindingResult bindingResult,
            HttpServletRequest servletRequest,
            RedirectAttributes redirectAttributes
    ) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/checkout";
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn đầy đủ địa chỉ nhận hàng và hình thức thanh toán.");
            return "redirect:/checkout";
        }

        String clientIp = servletRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = servletRequest.getRemoteAddr();
        }

        try {
            OrderGroup orderGroup = orderService.placeOrder(userDetails.getId(), request, clientIp);

            // Nếu thanh toán VNPay -> chuyển sang cổng thanh toán
            if (orderGroup.getPaymentMethod() == PaymentMethod.VNPAY) {
                String paymentUrl = vnpayService.createPaymentUrl(orderGroup, clientIp);
                return "redirect:" + paymentUrl;
            }

            // Thanh toán COD -> Đến trang thành công
            return "redirect:/checkout/success?groupCode=" + orderGroup.getCode();

        } catch (BusinessException e) {
            log.error("Place order failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/checkout";
        }
    }

    @GetMapping({"/checkout/success", "/orders/success"})
    public String orderSuccess(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam String groupCode,
            Model model
    ) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        OrderGroupSummaryResponse summary = orderService.getOrderGroupSummary(groupCode, userDetails.getId());
        model.addAttribute("summary", summary);

        return "web/order-success";
    }
}
