package com.jay.auth.repository;

import com.jay.auth.domain.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /**
     * 사용자의 최근 비밀번호 이력 조회 (최신순)
     */
    @Query("SELECT ph FROM PasswordHistory ph WHERE ph.user.id = :userId ORDER BY ph.changedAt DESC")
    List<PasswordHistory> findRecentByUserId(@Param("userId") Long userId);

    /**
     * 사용자의 비밀번호 이력 개수
     */
    long countByUserId(Long userId);

    /**
     * 오래된 비밀번호 이력 삭제 (최근 N개 유지)
     */
    @Modifying
    @Query(value = "DELETE FROM tb_password_history WHERE user_id = :userId AND history_id NOT IN " +
            "(SELECT * FROM (SELECT history_id FROM tb_password_history WHERE user_id = :userId " +
            "ORDER BY changed_at DESC LIMIT :keepCount) AS recent)", nativeQuery = true)
    void deleteOldHistories(@Param("userId") Long userId, @Param("keepCount") int keepCount);

    /**
     * 사용자의 모든 비밀번호 이력 삭제
     */
    void deleteByUserId(Long userId);
}
