package com.jay.auth.repository;

import com.jay.auth.domain.entity.UserTwoFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserTwoFactorRepository extends JpaRepository<UserTwoFactor, Long> {

    Optional<UserTwoFactor> findByUserId(Long userId);

    @Query("SELECT t FROM UserTwoFactor t JOIN FETCH t.user WHERE t.user.id = :userId")
    Optional<UserTwoFactor> findByUserIdWithUser(@Param("userId") Long userId);

    boolean existsByUserIdAndEnabled(Long userId, boolean enabled);

    void deleteByUserId(Long userId);
}
