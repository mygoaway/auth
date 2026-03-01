package com.jay.auth.service;

import com.jay.auth.domain.entity.IpAccessRule;
import com.jay.auth.domain.enums.IpRuleType;
import com.jay.auth.dto.request.IpRuleCreateRequest;
import com.jay.auth.dto.response.IpRuleResponse;
import com.jay.auth.repository.IpAccessRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IpAccessService {

    private final IpAccessRuleRepository ipAccessRuleRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String IP_BLOCK_CACHE_PREFIX = "ipblock:";
    private static final String IP_ALLOW_CACHE_PREFIX = "ipallow:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String CACHE_HIT = "1";
    private static final String CACHE_MISS = "0";

    /**
     * IP가 차단되어 있는지 확인한다 (Redis 캐시 우선).
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(String ip) {
        String cacheKey = IP_BLOCK_CACHE_PREFIX + ip;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return CACHE_HIT.equals(cached);
        }

        List<IpAccessRule> rules = ipAccessRuleRepository.findActiveByIp(ip);
        boolean blocked = rules.stream()
                .anyMatch(r -> r.getRuleType() == IpRuleType.BLOCK && !r.isExpired());

        redisTemplate.opsForValue().set(cacheKey, blocked ? CACHE_HIT : CACHE_MISS, CACHE_TTL);
        return blocked;
    }

    /**
     * IP가 명시적으로 허용되어 있는지 확인한다 (ALLOW 규칙 존재 여부).
     */
    @Transactional(readOnly = true)
    public boolean isAllowed(String ip) {
        String cacheKey = IP_ALLOW_CACHE_PREFIX + ip;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return CACHE_HIT.equals(cached);
        }

        List<IpAccessRule> rules = ipAccessRuleRepository.findActiveByIp(ip);
        boolean allowed = rules.stream()
                .anyMatch(r -> r.getRuleType() == IpRuleType.ALLOW && !r.isExpired());

        redisTemplate.opsForValue().set(cacheKey, allowed ? CACHE_HIT : CACHE_MISS, CACHE_TTL);
        return allowed;
    }

    /**
     * IP 규칙을 생성한다.
     */
    @Transactional
    public IpRuleResponse createRule(IpRuleCreateRequest request, Long adminId) {
        // 동일한 IP + 타입의 활성 규칙이 있으면 비활성화 후 재생성
        Optional<IpAccessRule> existing = ipAccessRuleRepository
                .findByIpAddressAndRuleTypeAndIsActiveTrue(request.getIpAddress(), request.getRuleType());
        existing.ifPresent(IpAccessRule::deactivate);

        IpAccessRule rule = IpAccessRule.builder()
                .ipAddress(request.getIpAddress())
                .ruleType(request.getRuleType())
                .reason(request.getReason())
                .createdBy(adminId)
                .expiredAt(request.getExpiredAt())
                .build();

        IpAccessRule saved = ipAccessRuleRepository.save(rule);
        evictCache(request.getIpAddress());
        log.info("IP rule created: ip={}, type={}, adminId={}", request.getIpAddress(), request.getRuleType(), adminId);
        return IpRuleResponse.from(saved);
    }

    /**
     * IP 규칙을 비활성화(삭제)한다.
     */
    @Transactional
    public void deleteRule(Long ruleId, Long adminId) {
        IpAccessRule rule = ipAccessRuleRepository.findById(ruleId)
                .orElseThrow(() -> new com.jay.auth.exception.IpRuleNotFoundException());
        rule.deactivate();
        evictCache(rule.getIpAddress());
        log.info("IP rule deleted: ruleId={}, adminId={}", ruleId, adminId);
    }

    /**
     * IP 규칙 목록 조회 (페이징).
     */
    @Transactional(readOnly = true)
    public Page<IpRuleResponse> getRules(int page, int size) {
        return ipAccessRuleRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(IpRuleResponse::from);
    }

    /**
     * 브루트포스 감지 시 IP를 자동으로 차단한다.
     */
    @Transactional
    public void autoBlock(String ip, String reason) {
        Optional<IpAccessRule> existing = ipAccessRuleRepository
                .findByIpAddressAndRuleTypeAndIsActiveTrue(ip, IpRuleType.BLOCK);
        if (existing.isPresent()) {
            return; // 이미 차단됨
        }

        IpAccessRule rule = IpAccessRule.builder()
                .ipAddress(ip)
                .ruleType(IpRuleType.BLOCK)
                .reason(reason)
                .createdBy(null) // 시스템 자동 차단
                .expiredAt(null) // 만료 없음
                .build();

        ipAccessRuleRepository.save(rule);
        evictCache(ip);
        log.warn("IP auto-blocked: ip={}, reason={}", ip, reason);
    }

    private void evictCache(String ip) {
        redisTemplate.delete(IP_BLOCK_CACHE_PREFIX + ip);
        redisTemplate.delete(IP_ALLOW_CACHE_PREFIX + ip);
    }
}
