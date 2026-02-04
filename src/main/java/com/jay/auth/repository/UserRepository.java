package com.jay.auth.repository;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
