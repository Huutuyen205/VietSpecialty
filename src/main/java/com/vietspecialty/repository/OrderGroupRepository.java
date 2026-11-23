package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.OrderGroup;
import com.vietspecialty.domain.enums.OrderGroupPaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderGroupRepository extends JpaRepository<OrderGroup, Long> {

    @EntityGraph(attributePaths = {"orders", "orders.shop"})
    Optional<OrderGroup> findByCode(String code);

    @EntityGraph(attributePaths = {"orders", "orders.shop"})
    Optional<OrderGroup> findByCodeAndUserId(String code, Long userId);

    @Query("SELECT og FROM OrderGroup og WHERE og.paymentMethod = com.vietspecialty.domain.enums.PaymentMethod.VNPAY " +
           "AND og.paymentStatus IN (com.vietspecialty.domain.enums.OrderGroupPaymentStatus.UNPAID, com.vietspecialty.domain.enums.OrderGroupPaymentStatus.FAILED) " +
           "AND og.paymentExpiresAt <= :now")
    List<OrderGroup> findExpiredVnpayGroups(@Param("now") LocalDateTime now);
}

