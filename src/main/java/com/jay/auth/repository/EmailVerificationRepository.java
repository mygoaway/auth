package com.jay.auth.repository;

import com.jay.auth.domain.entity.EmailVerification;
import com.jay.auth.domain.enums.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByTokenId(String tokenId);

    Optional<EmailVerification> findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse(
            String emailLowerEnc, VerificationType verificationType);

    @Query("SELECT e FROM EmailVerification e " +
            "WHERE e.emailLowerEnc = :emailLowerEnc " +
            "AND e.verificationType = :verificationType " +
            "AND e.isVerified = true " +
            "AND e.expiresAt > :now")
    Optional<EmailVerification> findVerifiedAndNotExpired(@Param("emailLowerEnc") String emailLowerEnc,
                                                          @Param("verificationType") VerificationType verificationType,
                                                          @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM EmailVerification e WHERE e.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM EmailVerification e WHERE e.emailLowerEnc = :emailLowerEnc AND e.verificationType = :verificationType")
    int deleteByEmailAndType(@Param("emailLowerEnc") String emailLowerEnc,
                             @Param("verificationType") VerificationType verificationType);
}
