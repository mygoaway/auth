package com.jay.auth.repository;

import com.jay.auth.domain.entity.SupportPost;
import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.domain.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportPostRepository extends JpaRepository<SupportPost, Long> {

    long countByStatus(PostStatus status);

    long countByCreatedAtAfter(java.time.LocalDateTime dateTime);

    @Query("SELECT p FROM SupportPost p WHERE " +
            "(p.isPrivate = false OR p.userId = :userId) " +
            "AND (:category IS NULL OR p.category = :category) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "ORDER BY p.createdAt DESC")
    Page<SupportPost> findAllForUser(
            @Param("userId") Long userId,
            @Param("category") PostCategory category,
            @Param("status") PostStatus status,
            Pageable pageable);

    @Query("SELECT p FROM SupportPost p WHERE " +
            "(:category IS NULL OR p.category = :category) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "ORDER BY p.createdAt DESC")
    Page<SupportPost> findAllForAdmin(
            @Param("category") PostCategory category,
            @Param("status") PostStatus status,
            Pageable pageable);
}
