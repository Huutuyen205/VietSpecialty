package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.VoucherUsage;
import com.vietspecialty.domain.enums.VoucherUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {

    long countByVoucherIdAndUserIdAndStatus(Long voucherId, Long userId, VoucherUsageStatus status);

    List<VoucherUsage> findByOrderId(Long orderId);

    List<VoucherUsage> findByOrderGroupId(Long orderGroupId);
}

