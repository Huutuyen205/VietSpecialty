package com.vietspecialty.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Hệ thống & Xác thực chung
    INTERNAL_SERVER_ERROR("SYS_001", "Đã xảy ra lỗi nội bộ hệ thống, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("VAL_001", "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("AUTH_001", "Vui lòng đăng nhập để thực hiện thao tác này", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH_002", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS("AUTH_003", "Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH_004", "Tài khoản đã bị khóa, vui lòng liên hệ ban quản trị để được hỗ trợ", HttpStatus.FORBIDDEN),
    EMAIL_ALREADY_EXISTS("AUTH_005", "Email đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    WRONG_PASSWORD("AUTH_006", "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND("USER_001", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),

    // Gian hàng (Shop)
    SHOP_NOT_FOUND("SHOP_001", "Không tìm thấy gian hàng", HttpStatus.NOT_FOUND),
    SHOP_NAME_EXISTS("SHOP_002", "Tên gian hàng đã được sử dụng, vui lòng chọn tên khác", HttpStatus.CONFLICT),
    SHOP_PENDING_APPROVAL("SHOP_003", "Yêu cầu mở gian hàng của bạn đang chờ quản trị viên xét duyệt", HttpStatus.BAD_REQUEST),
    SHOP_NOT_APPROVED("SHOP_004", "Gian hàng chưa được duyệt hoặc đang bị đình chỉ hoạt động", HttpStatus.FORBIDDEN),
    CANNOT_BUY_OWN_PRODUCT("SHOP_005", "Người bán không được phép mua sản phẩm của chính gian hàng mình", HttpStatus.BAD_REQUEST),

    // Sản phẩm (Product)
    PRODUCT_NOT_FOUND("PROD_001", "Không tìm thấy sản phẩm yêu cầu", HttpStatus.NOT_FOUND),
    PRODUCT_IMAGE_REQUIRED("PROD_002", "Vui lòng thêm ít nhất một ảnh sản phẩm", HttpStatus.BAD_REQUEST),
    PRODUCT_PRICE_INVALID("PROD_003", "Giá sản phẩm phải lớn hơn 0", HttpStatus.BAD_REQUEST),
    PRODUCT_OUT_OF_STOCK("PROD_004", "Sản phẩm tạm thời hết hàng", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_STOCK("PROD_005", "Số lượng sản phẩm trong kho không đủ đáp ứng", HttpStatus.BAD_REQUEST),

    // Giỏ hàng (Cart)
    CART_ITEM_NOT_FOUND("CART_001", "Không tìm thấy sản phẩm trong giỏ hàng", HttpStatus.NOT_FOUND),
    CART_EMPTY("CART_002", "Giỏ hàng hiện đang trống", HttpStatus.BAD_REQUEST),

    // Mã giảm giá (Voucher)
    VOUCHER_NOT_FOUND("VOUCHER_001", "Mã giảm giá không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    VOUCHER_EXPIRED("VOUCHER_002", "Mã giảm giá đã hết hạn sử dụng", HttpStatus.BAD_REQUEST),
    VOUCHER_NOT_STARTED("VOUCHER_003", "Mã giảm giá chưa đến thời gian áp dụng", HttpStatus.BAD_REQUEST),
    VOUCHER_USAGE_EXCEEDED("VOUCHER_004", "Mã giảm giá đã hết lượt sử dụng trên hệ thống", HttpStatus.BAD_REQUEST),
    VOUCHER_USER_LIMIT_EXCEEDED("VOUCHER_005", "Bạn đã dùng hết lượt cho phép của mã giảm giá này", HttpStatus.BAD_REQUEST),
    VOUCHER_MIN_ORDER_NOT_MET("VOUCHER_006", "Đơn hàng chưa đạt giá trị tối thiểu để áp dụng mã giảm giá này", HttpStatus.BAD_REQUEST),
    VOUCHER_SHOP_MISMATCH("VOUCHER_007", "Mã giảm giá không áp dụng cho gian hàng này", HttpStatus.BAD_REQUEST),

    // Đơn hàng (Order)
    ORDER_NOT_FOUND("ORDER_001", "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND),
    ORDER_GROUP_NOT_FOUND("ORDER_002", "Không tìm thấy nhóm đơn hàng", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATUS_TRANSITION("ORDER_003", "Trạng thái đơn hàng không hợp lệ để thực hiện thao tác", HttpStatus.BAD_REQUEST),
    CANNOT_CANCEL_ORDER("ORDER_004", "Đơn hàng không thể hủy ở trạng thái hiện tại", HttpStatus.BAD_REQUEST),
    ORDER_EXPIRED("ORDER_005", "Đơn hàng đã hết hạn thanh toán", HttpStatus.BAD_REQUEST),
    PRICE_CHANGED("ORDER_006", "Giá sản phẩm hoặc phí vận chuyển đã thay đổi, vui lòng xác nhận lại đơn hàng", HttpStatus.CONFLICT),
    CHECKOUT_TOKEN_INVALID("ORDER_007", "Phiên đặt hàng không hợp lệ hoặc đã được sử dụng, vui lòng thử lại", HttpStatus.BAD_REQUEST),

    // Thanh toán VNPay
    PAYMENT_FAILED("PAY_001", "Giao dịch thanh toán không thành công", HttpStatus.BAD_REQUEST),
    PAYMENT_INVALID_SIGNATURE("PAY_002", "Chữ ký xác thực dữ liệu thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_TRANSACTION_NOT_FOUND("PAY_003", "Không tìm thấy thông tin giao dịch thanh toán", HttpStatus.NOT_FOUND),
    PAYMENT_AMOUNT_MISMATCH("PAY_004", "Số tiền thanh toán không khớp với hóa đơn", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_PROCESSED("PAY_005", "Giao dịch này đã được hệ thống xử lý trước đó", HttpStatus.OK),

    // Chatbot AI
    AI_SERVICE_UNAVAILABLE("AI_001", "Trợ lý AI tạm thời gián đoạn kết nối, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),
    AI_RATE_LIMITED("AI_002", "Bạn gửi tin nhắn quá nhanh, vui lòng chờ ít phút để tiếp tục", HttpStatus.TOO_MANY_REQUESTS),

    // Đánh giá & Bình luận
    REVIEW_NOT_ELIGIBLE("REV_001", "Chỉ có thể đánh giá sản phẩm sau khi đơn hàng đã được giao thành công", HttpStatus.BAD_REQUEST),
    REVIEW_ALREADY_EXISTS("REV_002", "Bạn đã đánh giá sản phẩm này cho đơn hàng tương ứng", HttpStatus.CONFLICT),
    ADDRESS_NOT_FOUND("ADDR_001", "Không tìm thấy địa chỉ nhận hàng", HttpStatus.NOT_FOUND);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}

