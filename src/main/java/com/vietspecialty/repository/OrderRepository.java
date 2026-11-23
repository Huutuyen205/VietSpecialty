package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.Order;
import com.vietspecialty.domain.enums.OrderPaymentStatus;
import com.vietspecialty.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCode(String code);

    Optional<Order> findByCodeAndUserId(String code, Long userId);

    Optional<Order> findByCodeAndShopId(String code, Long shopId);

    @EntityGraph(attributePaths = {"shop", "items"})
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"shop", "items"})
    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByShopIdOrderByCreatedAtDesc(Long shopId, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByShopIdAndStatusOrderByCreatedAtDesc(Long shopId, OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o WHERE o.shop.id = :shopId " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:search IS NULL OR LOWER(o.code) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(o.receiverName) LIKE LOWER(CONCAT('%', :search, '%')) OR o.receiverPhone LIKE CONCAT('%', :search, '%')) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> searchSellerOrders(@Param("shopId") Long shopId, @Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);

    long countByShopIdAndStatus(Long shopId, OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.status = com.vietspecialty.domain.enums.OrderStatus.SHIPPING AND o.shippedAt <= :cutoffTime")
    List<Order> findOrdersForAutoDeliver(@Param("cutoffTime") LocalDateTime cutoffTime);

    @EntityGraph(attributePaths = {"user", "shop", "items"})
    Page<Order> findByPaymentStatusOrderByCreatedAtDesc(OrderPaymentStatus paymentStatus, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "shop", "items"})
    Page<Order> findByPaymentStatusInOrderByCreatedAtDesc(List<OrderPaymentStatus> paymentStatuses, Pageable pageable);

    long countByPaymentStatus(OrderPaymentStatus paymentStatus);

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByShopIdAndStatusOrderByCreatedAtDesc(Long shopId, OrderStatus status);

    List<Order> findByOrderGroupId(Long orderGroupId);

    long countByShopId(Long shopId);

    long countByStatus(OrderStatus status);

    @Query(value = "SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o WHERE o.shop_id = :shopId AND o.status = :status", nativeQuery = true)
    java.math.BigDecimal sumRevenueByShopAndStatus(@Param("shopId") Long shopId, @Param("status") String status);

    @Query(value = "SELECT DATE_FORMAT(o.created_at, '%d/%m') as day_label, COALESCE(SUM(o.total_amount), 0) as rev, COUNT(o.id) as cnt " +
                   "FROM orders o WHERE o.shop_id = :shopId AND o.status = 'DELIVERED' AND o.created_at >= :startDate " +
                   "GROUP BY DATE_FORMAT(o.created_at, '%d/%m'), DATE(o.created_at) ORDER BY DATE(o.created_at) ASC", nativeQuery = true)
    List<Object[]> getDailyRevenueByShop(@Param("shopId") Long shopId, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT r.id, COALESCE(SUM(o.totalAmount), 0), COUNT(o) " +
           "FROM Order o JOIN o.shop s JOIN s.province p JOIN p.region r " +
           "WHERE o.status = :status GROUP BY r.id")
    List<Object[]> sumRevenueByRegion(@Param("status") OrderStatus status);

    @Query(value = "SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o WHERE o.status = :status", nativeQuery = true)
    java.math.BigDecimal sumTotalRevenuePlatform(@Param("status") String status);

    @Query(value = "SELECT DATE_FORMAT(o.created_at, '%d/%m') as day_label, COALESCE(SUM(o.total_amount), 0) as rev, COUNT(o.id) as cnt " +
                   "FROM orders o WHERE o.status = 'DELIVERED' AND o.created_at >= :startDate " +
                   "GROUP BY DATE_FORMAT(o.created_at, '%d/%m'), DATE(o.created_at) ORDER BY DATE(o.created_at) ASC", nativeQuery = true)
    List<Object[]> getDailyRevenuePlatform(@Param("startDate") LocalDateTime startDate);
}

