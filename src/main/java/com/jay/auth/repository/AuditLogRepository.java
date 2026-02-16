package com.jay.auth.repository;

import com.jay.auth.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.createdAt >= :since AND a.createdAt < :until ORDER BY a.createdAt DESC")
    List<AuditLog> findByUserIdAndPeriod(@Param("userId") Long userId,
                                         @Param("since") LocalDateTime since,
                                         @Param("until") LocalDateTime until);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :before")
    void deleteOldLogs(@Param("before") LocalDateTime before);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentLogs(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.action = :action AND a.createdAt >= :since")
    long countByActionSince(@Param("action") String action, @Param("since") LocalDateTime since);
}
