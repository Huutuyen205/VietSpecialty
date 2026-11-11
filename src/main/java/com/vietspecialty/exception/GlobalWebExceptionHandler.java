package com.vietspecialty.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice(annotations = Controller.class)
public class GlobalWebExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException ex, HttpServletRequest request, Model model) {
        log.warn("Web business error [{}]: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("errorCode", ex.getErrorCode().getCode());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/500";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Model model) {
        log.warn("Web 403 Forbidden [{}]: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/403";
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public String handleNotFound(Exception ex, HttpServletRequest request, Model model) {
        log.debug("Web 404 Not Found: {}", request.getRequestURI());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/404";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception ex, HttpServletRequest request, Model model) {
        log.error("Web 500 Internal Error on URI [{}]: ", request.getRequestURI(), ex);
        model.addAttribute("errorMessage", "Hệ thống gặp sự cố trong quá trình xử lý, vui lòng tải lại trang.");
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/500";
    }
}

