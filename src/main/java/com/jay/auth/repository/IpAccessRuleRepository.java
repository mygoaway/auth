package com.jay.auth.repository;

import com.jay.auth.domain.entity.IpAccessRule;
import com.jay.auth.domain.enums.IpRuleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IpAccessRuleRepository extends JpaRepository<IpAccessRule, Long> {

    @Query("SELECT r FROM IpAccessRule r WHERE r.ipAddress = :ip AND r.isActive = true " +
           "AND (r.expiredAt IS NULL OR r.expiredAt > CURRENT_TIMESTAMP)")
    List<IpAccessRule> findActiveByIp(@Param("ip") String ip);

    @Query("SELECT r FROM IpAccessRule r WHERE r.isActive = true " +
           "AND (r.expiredAt IS NULL OR r.expiredAt > CURRENT_TIMESTAMP) " +
           "ORDER BY r.createdAt DESC")
    List<IpAccessRule> findAllActive();

    Page<IpAccessRule> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<IpAccessRule> findByIpAddressAndRuleTypeAndIsActiveTrue(String ipAddress, IpRuleType ruleType);
}
