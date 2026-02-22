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

    // 국가/도시별 로그인 집계 (heatmap)
    @Query("SELECT h.location, COUNT(h), SUM(CASE WHEN h.isSuccess = false THEN 1 ELSE 0 END) " +
            "FROM LoginHistory h WHERE h.createdAt >= :since AND h.location IS NOT NULL " +
            "GROUP BY h.location ORDER BY COUNT(h) DESC")
    List<Object[]> countLoginsByLocation(@Param("since") LocalDateTime since);

    // IP별 실패 시도 집계 (hotspot)
    @Query("SELECT h.ipAddress, h.location, COUNT(h), MAX(h.createdAt) " +
            "FROM LoginHistory h WHERE h.isSuccess = false AND h.createdAt >= :since AND h.ipAddress IS NOT NULL " +
            "GROUP BY h.ipAddress, h.location ORDER BY COUNT(h) DESC")
    List<Object[]> countFailuresByIp(@Param("since") LocalDateTime since, Pageable pageable);

    // 시간대별 로그인 집계 (timeline)
    @Query("SELECT FUNCTION('HOUR', h.createdAt), " +
            "SUM(CASE WHEN h.isSuccess = true THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN h.isSuccess = false THEN 1 ELSE 0 END) " +
            "FROM LoginHistory h WHERE h.createdAt >= :since " +
            "GROUP BY FUNCTION('HOUR', h.createdAt) ORDER BY FUNCTION('HOUR', h.createdAt) ASC")
    List<Object[]> countLoginsByHour(@Param("since") LocalDateTime since);

    // 사용자 개인 위치별 로그인 집계
    @Query("SELECT h.location, " +
            "SUM(CASE WHEN h.isSuccess = true THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN h.isSuccess = false THEN 1 ELSE 0 END), " +
            "MAX(h.createdAt) " +
            "FROM LoginHistory h WHERE h.userId = :userId AND h.createdAt >= :since AND h.location IS NOT NULL " +
            "GROUP BY h.location ORDER BY MAX(h.createdAt) DESC")
    List<Object[]> countLoginsByLocationForUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
