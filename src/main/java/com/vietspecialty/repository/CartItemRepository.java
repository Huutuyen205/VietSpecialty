package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.shop", "product.shop.owner", "product.images"})
    List<CartItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"product", "product.shop", "product.shop.owner", "product.images"})
    List<CartItem> findByUserIdAndIdIn(Long userId, List<Long> ids);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    long countByUserId(Long userId);

    void deleteByUserIdAndIdIn(Long userId, List<Long> ids);

    void deleteAllByUserId(Long userId);
}

