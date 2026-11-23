package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.ProductComment;
import com.vietspecialty.domain.enums.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCommentRepository extends JpaRepository<ProductComment, Long> {

    @EntityGraph(attributePaths = {"user", "replies", "replies.user"})
    Page<ProductComment> findByProductIdAndParentIsNullAndStatusOrderByCreatedAtDesc(Long productId, CommentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    List<ProductComment> findByParentIdAndStatusOrderByCreatedAtAsc(Long parentId, CommentStatus status);

    @EntityGraph(attributePaths = {"user", "product", "replies", "replies.user"})
    Page<ProductComment> findByProductShopIdAndParentIsNullOrderByCreatedAtDesc(Long shopId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product", "replies", "replies.user"})
    Page<ProductComment> findByParentIsNullOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<ProductComment> findByStatusOrderByCreatedAtDesc(CommentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<ProductComment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

