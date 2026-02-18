package com.jay.auth.repository;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserUuid(String userUuid);

    Optional<User> findByEmailLowerEnc(String emailLowerEnc);

    boolean existsByEmailLowerEnc(String emailLowerEnc);

    @Query("SELECT u FROM User u WHERE u.emailLowerEnc = :emailLowerEnc AND u.status = :status")
    Optional<User> findByEmailLowerEncAndStatus(@Param("emailLowerEnc") String emailLowerEnc,
                                                 @Param("status") UserStatus status);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.channels WHERE u.id = :userId")
    Optional<User> findByIdWithChannels(@Param("userId") Long userId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.signInInfo WHERE u.id = :userId")
    Optional<User> findByIdWithSignInInfo(@Param("userId") Long userId);

    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.signInInfo " +
            "LEFT JOIN FETCH u.channels " +
            "WHERE u.userUuid = :userUuid")
    Optional<User> findByUserUuidWithDetails(@Param("userUuid") String userUuid);

    boolean existsByNicknameLowerEnc(String nicknameLowerEnc);

    boolean existsByRecoveryEmailLowerEnc(String recoveryEmailLowerEnc);

    Optional<User> findByRecoveryEmailLowerEnc(String recoveryEmailLowerEnc);

    long countByStatus(UserStatus status);

    long countByCreatedAtAfter(LocalDateTime dateTime);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.channels LEFT JOIN FETCH u.signInInfo ORDER BY u.createdAt DESC")
    List<User> findRecentUsersWithChannels(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.status = 'PENDING_DELETE' AND u.deletionRequestedAt < :cutoffDate")
    List<User> findExpiredPendingDeletions(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT u FROM User u LEFT JOIN u.signInInfo si " +
            "WHERE u.status = 'ACTIVE' " +
            "AND (si IS NULL OR si.lastLoginAt < :cutoffDate) " +
            "AND u.createdAt < :cutoffDate")
    List<User> findDormantCandidates(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.channels LEFT JOIN FETCH u.signInInfo " +
            "WHERE (:keyword IS NULL OR u.emailLowerEnc = :keyword OR u.nicknameEnc LIKE %:keyword% OR u.userUuid = :keyword) " +
            "AND (:status IS NULL OR u.status = :status)")
    org.springframework.data.domain.Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT FUNCTION('DATE', u.createdAt) AS signupDate, COUNT(u) " +
            "FROM User u WHERE u.createdAt >= :since " +
            "GROUP BY FUNCTION('DATE', u.createdAt) ORDER BY signupDate DESC")
    List<Object[]> countDailySignups(@Param("since") LocalDateTime since);
}
