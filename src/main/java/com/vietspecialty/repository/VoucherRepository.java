package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.Voucher;
import com.vietspecialty.domain.enums.VoucherScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<Voucher> findByScope(VoucherScope scope, Pageable pageable);

    Page<Voucher> findByShopId(Long shopId, Pageable pageable);

    List<Voucher> findByShopIdAndIsActiveTrue(Long shopId);

    List<Voucher> findByScopeAndIsActiveTrue(VoucherScope scope);

    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount + 1 WHERE v.id = :id AND (v.usageLimit IS NULL OR v.usedCount < v.usageLimit)")
    int incrementUsedCountAtomic(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount - 1 WHERE v.id = :id AND v.usedCount > 0")
    int decrementUsedCountAtomic(@Param("id") Long id);
}

