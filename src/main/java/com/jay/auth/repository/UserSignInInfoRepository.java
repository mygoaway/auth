package com.jay.auth.repository;

import com.jay.auth.domain.entity.UserSignInInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSignInInfoRepository extends JpaRepository<UserSignInInfo, Long> {

    Optional<UserSignInInfo> findByLoginEmailLowerEnc(String loginEmailLowerEnc);

    boolean existsByLoginEmailLowerEnc(String loginEmailLowerEnc);

    Optional<UserSignInInfo> findByUserId(Long userId);

    @Query("SELECT s FROM UserSignInInfo s JOIN FETCH s.user WHERE s.loginEmailLowerEnc = :loginEmailLowerEnc")
    Optional<UserSignInInfo> findByLoginEmailLowerEncWithUser(@Param("loginEmailLowerEnc") String loginEmailLowerEnc);

    @Query("SELECT s FROM UserSignInInfo s JOIN FETCH s.user u WHERE s.loginEmailLowerEnc = :loginEmailLowerEnc AND u.status = 'ACTIVE'")
    Optional<UserSignInInfo> findActiveByLoginEmailLowerEnc(@Param("loginEmailLowerEnc") String loginEmailLowerEnc);

    @Query("SELECT s FROM UserSignInInfo s JOIN FETCH s.user u WHERE u.recoveryEmailLowerEnc = :recoveryEmailLowerEnc")
    Optional<UserSignInInfo> findByRecoveryEmailLowerEncWithUser(@Param("recoveryEmailLowerEnc") String recoveryEmailLowerEnc);

    @Query("SELECT s FROM UserSignInInfo s JOIN FETCH s.user u WHERE u.recoveryEmailLowerEnc = :recoveryEmailLowerEnc")
    List<UserSignInInfo> findAllByRecoveryEmailLowerEncWithUser(@Param("recoveryEmailLowerEnc") String recoveryEmailLowerEnc);

    /**
     * 비밀번호 업데이트 시간이 특정 기간 사이인 활성 사용자 조회 (만료 임박/만료 알림용)
     * passwordUpdatedAt이 [from, to] 범위에 있는 사용자 = 만료 예정일이 오늘로부터 특정 일수 남은 사용자
     */
    @Query("SELECT s FROM UserSignInInfo s JOIN FETCH s.user u " +
           "WHERE u.status = 'ACTIVE' " +
           "AND s.passwordUpdatedAt IS NOT NULL " +
           "AND s.passwordUpdatedAt BETWEEN :from AND :to")
    List<UserSignInInfo> findUsersWithPasswordUpdatedBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
