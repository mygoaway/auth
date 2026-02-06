package com.jay.auth.repository;

import com.jay.auth.domain.entity.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    Optional<PhoneVerification> findByTokenId(String tokenId);

    Optional<PhoneVerification> findByPhoneLowerEncAndVerificationCodeAndIsVerifiedFalse(
            String phoneLowerEnc, String verificationCode);

    Optional<PhoneVerification> findByPhoneLowerEncAndIsVerifiedFalse(String phoneLowerEnc);

    @Query("SELECT p FROM PhoneVerification p " +
            "WHERE p.tokenId = :tokenId " +
            "AND p.isVerified = true " +
            "AND p.expiresAt > :now")
    Optional<PhoneVerification> findByTokenIdAndVerifiedTrue(@Param("tokenId") String tokenId,
                                                              @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PhoneVerification p WHERE p.expiresAt < :now")
    int deleteExpiredVerifications(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PhoneVerification p WHERE p.phoneLowerEnc = :phoneLowerEnc")
    int deleteByPhoneLowerEnc(@Param("phoneLowerEnc") String phoneLowerEnc);
}
