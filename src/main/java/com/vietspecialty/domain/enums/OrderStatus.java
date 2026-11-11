package com.vietspecialty.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING_PAYMENT("Chờ thanh toán"),
    PENDING_CONFIRM("Chờ xác nhận"),
    PREPARING("Đang chuẩn bị hàng"),
    SHIPPING("Đang giao hàng"),
    DELIVERED("Đã giao hàng"),
    CANCELLED("Đã hủy");

    private final String description;

    /**
     * Kiểm tra trạng thái hiện tại có được phép chuyển sang trạng thái mục tiêu hay không.
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) return false;

        return switch (this) {
            case PENDING_PAYMENT -> target == PENDING_CONFIRM || target == CANCELLED;
            case PENDING_CONFIRM -> target == PREPARING || target == CANCELLED;
            case PREPARING -> target == SHIPPING;
            case SHIPPING -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false; // Trạng thái cuối, không thể chuyển đổi tiếp
        };
    }

    /**
     * Kiểm tra tác nhân có quyền thực hiện chuyển trạng thái hay không.
     */
    public boolean isAllowedActor(OrderStatus target, ActorType actor) {
        if (!canTransitionTo(target)) return false;

        return switch (this) {
            case PENDING_PAYMENT -> switch (target) {
                case PENDING_CONFIRM -> actor == ActorType.SYSTEM; // Qua VNPay IPN / Return
                case CANCELLED -> actor == ActorType.BUYER || actor == ActorType.SYSTEM; // Quá hạn hoặc khách hủy
                default -> false;
            };
            case PENDING_CONFIRM -> switch (target) {
                case PREPARING -> actor == ActorType.SELLER;
                case CANCELLED -> actor == ActorType.BUYER || actor == ActorType.SELLER || actor == ActorType.ADMIN;
                default -> false;
            };
            case PREPARING -> target == SHIPPING && actor == ActorType.SELLER;
            case SHIPPING -> target == DELIVERED && (actor == ActorType.BUYER || actor == ActorType.SELLER || actor == ActorType.SYSTEM);
            case DELIVERED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}

