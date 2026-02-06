package com.jay.auth.repository;

import com.jay.auth.domain.entity.UserSignInInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
