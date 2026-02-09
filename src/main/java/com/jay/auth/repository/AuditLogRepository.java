package com.jay.auth.repository;

import com.jay.auth.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :before")
    void deleteOldLogs(@Param("before") LocalDateTime before);
}
