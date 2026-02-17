package com.jay.auth.repository;

import com.jay.auth.domain.entity.UserPasskey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPasskeyRepository extends JpaRepository<UserPasskey, Long> {

    Optional<UserPasskey> findByCredentialId(String credentialId);

    List<UserPasskey> findByUserId(Long userId);

    @Query("SELECT p FROM UserPasskey p JOIN FETCH p.user WHERE p.credentialId = :credentialId")
    Optional<UserPasskey> findByCredentialIdWithUser(@Param("credentialId") String credentialId);

    Optional<UserPasskey> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
