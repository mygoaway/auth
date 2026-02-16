package com.jay.auth.repository;

import com.jay.auth.domain.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    List<LoginHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<LoginHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT h FROM LoginHistory h WHERE h.userId = :userId ORDER BY h.createdAt DESC")
    List<LoginHistory> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(h) FROM LoginHistory h WHERE h.userId = :userId AND h.isSuccess = false " +
            "AND h.createdAt > :since")
    long countFailedLoginsSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT h FROM LoginHistory h WHERE h.userId = :userId AND h.createdAt >= :since AND h.createdAt < :until ORDER BY h.createdAt DESC")
    List<LoginHistory> findByUserIdAndPeriod(@Param("userId") Long userId,
                                             @Param("since") LocalDateTime since,
                                             @Param("until") LocalDateTime until);

    @Modifying
    @Query("DELETE FROM LoginHistory h WHERE h.createdAt < :before")
    void deleteOldHistory(@Param("before") LocalDateTime before);

    @Modifying
    @Query("DELETE FROM LoginHistory h WHERE h.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    long countByCreatedAtAfter(LocalDateTime dateTime);

    @Query("SELECT FUNCTION('DATE', h.createdAt) AS loginDate, COUNT(h) " +
            "FROM LoginHistory h WHERE h.createdAt >= :since AND h.isSuccess = true " +
            "GROUP BY FUNCTION('DATE', h.createdAt) ORDER BY loginDate DESC")
    List<Object[]> countDailyLogins(@Param("since") LocalDateTime since);

    @Query("SELECT h FROM LoginHistory h WHERE h.isSuccess = false AND h.createdAt >= :since ORDER BY h.createdAt DESC")
    List<LoginHistory> findRecentFailedLogins(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT h.userId) FROM LoginHistory h WHERE h.isSuccess = true AND h.createdAt >= :since")
    long countDistinctActiveUsersSince(@Param("since") LocalDateTime since);
}
