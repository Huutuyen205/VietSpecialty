package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTxnRef(String txnRef);

    List<Payment> findByOrderGroupIdOrderByCreatedAtDesc(Long orderGroupId);

    Optional<Payment> findTopByOrderGroupIdOrderByCreatedAtDesc(Long orderGroupId);
}

