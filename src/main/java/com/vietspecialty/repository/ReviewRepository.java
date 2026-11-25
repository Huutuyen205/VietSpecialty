package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.Review;
import com.vietspecialty.domain.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByOrderItemId(Long orderItemId);

    boolean existsByOrderItemId(Long orderItemId);

    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProductIdAndStatusAndRating(Long productId, ReviewStatus status, Integer rating, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findByProductShopIdOrderByCreatedAtDesc(Long shopId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.status = com.vietspecialty.domain.enums.ReviewStatus.VISIBLE")
    Double calculateAvgRating(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.status = com.vietspecialty.domain.enums.ReviewStatus.VISIBLE")
    Integer countVisibleReviews(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

