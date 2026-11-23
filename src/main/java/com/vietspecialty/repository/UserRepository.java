package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    default Optional<User> findByEmail(String email) {
        return findByEmailIgnoreCase(email);
    }

    boolean existsByEmailIgnoreCase(String email);

    default boolean existsByEmail(String email) {
        return existsByEmailIgnoreCase(email);
    }

    Page<User> findByRole(Role role, Pageable pageable);

    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT u FROM User u WHERE " +
            "(:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR (u.phone IS NOT NULL AND u.phone LIKE CONCAT('%', :keyword, '%'))) " +
            "AND (:role IS NULL OR u.role = :role) " +
            "AND (:status IS NULL OR u.status = :status) " +
            "ORDER BY u.createdAt DESC"
    )
    Page<User> searchUsers(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("role") Role role,
            @org.springframework.data.repository.query.Param("status") com.vietspecialty.domain.enums.UserStatus status,
            Pageable pageable
    );
}

