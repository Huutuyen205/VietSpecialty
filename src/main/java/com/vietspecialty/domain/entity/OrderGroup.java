package com.vietspecialty.domain.entity;

import com.vietspecialty.domain.enums.OrderGroupPaymentStatus;
import com.vietspecialty.domain.enums.PaymentMethod;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, columnDefinition = "ENUM('COD','VNPAY')")
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_voucher_id")
    private Voucher platformVoucher;

    @Column(name = "items_subtotal", nullable = false, precision = 15, scale = 0)
    private BigDecimal itemsSubtotal;

    @Column(name = "shipping_total", nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal shippingTotal = BigDecimal.ZERO;

    @Column(name = "shop_discount_total", nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal shopDiscountTotal = BigDecimal.ZERO;

    @Column(name = "platform_discount_total", nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal platformDiscountTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 15, scale = 0)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, columnDefinition = "ENUM('UNPAID','PAID','FAILED','EXPIRED','NOT_REQUIRED')")
    private OrderGroupPaymentStatus paymentStatus;

    @Column(name = "payment_expires_at")
    private LocalDateTime paymentExpiresAt;

    @OneToMany(mappedBy = "orderGroup", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

