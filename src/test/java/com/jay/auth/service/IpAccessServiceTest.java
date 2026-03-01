package com.jay.auth.service;

import com.jay.auth.domain.entity.IpAccessRule;
import com.jay.auth.domain.enums.IpRuleType;
import com.jay.auth.dto.request.IpRuleCreateRequest;
import com.jay.auth.dto.response.IpRuleResponse;
import com.jay.auth.exception.IpRuleNotFoundException;
import com.jay.auth.repository.IpAccessRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("IpAccessService 테스트")
class IpAccessServiceTest {

    @InjectMocks
    private IpAccessService ipAccessService;

    @Mock
    private IpAccessRuleRepository ipAccessRuleRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Nested
    @DisplayName("isBlocked()")
    class IsBlocked {

        @Test
        @DisplayName("Redis 캐시 HIT — 차단 상태이면 true를 반환한다")
        void blockedFromCache() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ipblock:1.2.3.4")).willReturn("1");

            assertThat(ipAccessService.isBlocked("1.2.3.4")).isTrue();
            then(ipAccessRuleRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Redis 캐시 HIT — 허용 상태이면 false를 반환한다")
        void notBlockedFromCache() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ipblock:1.2.3.4")).willReturn("0");

            assertThat(ipAccessService.isBlocked("1.2.3.4")).isFalse();
        }

        @Test
        @DisplayName("캐시 MISS — BLOCK 규칙이 있으면 true를 반환하고 캐시에 저장한다")
        void blockedFromDb() {
            IpAccessRule rule = IpAccessRule.builder()
                    .ipAddress("1.2.3.4")
                    .ruleType(IpRuleType.BLOCK)
                    .reason("테스트 차단")
                    .createdBy(1L)
                    .build();

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ipblock:1.2.3.4")).willReturn(null);
            given(ipAccessRuleRepository.findActiveByIp("1.2.3.4")).willReturn(List.of(rule));

            assertThat(ipAccessService.isBlocked("1.2.3.4")).isTrue();
            then(valueOperations).should().set(eq("ipblock:1.2.3.4"), eq("1"), any());
        }

        @Test
        @DisplayName("캐시 MISS — 활성 규칙이 없으면 false를 반환한다")
        void notBlockedFromDb() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ipblock:5.6.7.8")).willReturn(null);
            given(ipAccessRuleRepository.findActiveByIp("5.6.7.8")).willReturn(List.of());

            assertThat(ipAccessService.isBlocked("5.6.7.8")).isFalse();
            then(valueOperations).should().set(eq("ipblock:5.6.7.8"), eq("0"), any());
        }
    }

    @Nested
    @DisplayName("isAllowed()")
    class IsAllowed {

        @Test
        @DisplayName("ALLOW 규칙이 있으면 true를 반환한다")
        void allowedFromDb() {
            IpAccessRule rule = IpAccessRule.builder()
                    .ipAddress("10.0.0.1")
                    .ruleType(IpRuleType.ALLOW)
                    .createdBy(1L)
                    .build();

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ipallow:10.0.0.1")).willReturn(null);
            given(ipAccessRuleRepository.findActiveByIp("10.0.0.1")).willReturn(List.of(rule));

            assertThat(ipAccessService.isAllowed("10.0.0.1")).isTrue();
        }

        @Test
        @DisplayName("ALLOW 규칙이 없으면 false를 반환한다")
        void notAllowed() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ipallow:10.0.0.2")).willReturn(null);
            given(ipAccessRuleRepository.findActiveByIp("10.0.0.2")).willReturn(List.of());

            assertThat(ipAccessService.isAllowed("10.0.0.2")).isFalse();
        }
    }

    @Nested
    @DisplayName("createRule()")
    class CreateRule {

        @Test
        @DisplayName("새 BLOCK 규칙을 성공적으로 생성한다")
        void createBlockRule() {
            IpRuleCreateRequest request = new IpRuleCreateRequest();
            ReflectionTestUtils.setField(request, "ipAddress", "192.168.1.1");
            ReflectionTestUtils.setField(request, "ruleType", IpRuleType.BLOCK);
            ReflectionTestUtils.setField(request, "reason", "의심스러운 접근");
            ReflectionTestUtils.setField(request, "expiredAt", null);

            IpAccessRule saved = IpAccessRule.builder()
                    .ipAddress("192.168.1.1")
                    .ruleType(IpRuleType.BLOCK)
                    .reason("의심스러운 접근")
                    .createdBy(1L)
                    .build();
            ReflectionTestUtils.setField(saved, "id", 10L);

            given(ipAccessRuleRepository.findByIpAddressAndRuleTypeAndIsActiveTrue("192.168.1.1", IpRuleType.BLOCK))
                    .willReturn(Optional.empty());
            given(ipAccessRuleRepository.save(any())).willReturn(saved);
            given(redisTemplate.delete(anyString())).willReturn(true);

            IpRuleResponse response = ipAccessService.createRule(request, 1L);

            assertThat(response.getIpAddress()).isEqualTo("192.168.1.1");
            assertThat(response.getRuleType()).isEqualTo(IpRuleType.BLOCK);
        }

        @Test
        @DisplayName("기존 활성 규칙이 있으면 비활성화 후 새 규칙을 생성한다")
        void createRuleDeactivatesExisting() {
            IpRuleCreateRequest request = new IpRuleCreateRequest();
            ReflectionTestUtils.setField(request, "ipAddress", "192.168.1.2");
            ReflectionTestUtils.setField(request, "ruleType", IpRuleType.BLOCK);
            ReflectionTestUtils.setField(request, "reason", "재차단");
            ReflectionTestUtils.setField(request, "expiredAt", null);

            IpAccessRule existing = IpAccessRule.builder()
                    .ipAddress("192.168.1.2")
                    .ruleType(IpRuleType.BLOCK)
                    .createdBy(1L)
                    .build();

            IpAccessRule saved = IpAccessRule.builder()
                    .ipAddress("192.168.1.2")
                    .ruleType(IpRuleType.BLOCK)
                    .reason("재차단")
                    .createdBy(1L)
                    .build();
            ReflectionTestUtils.setField(saved, "id", 11L);

            given(ipAccessRuleRepository.findByIpAddressAndRuleTypeAndIsActiveTrue("192.168.1.2", IpRuleType.BLOCK))
                    .willReturn(Optional.of(existing));
            given(ipAccessRuleRepository.save(any())).willReturn(saved);
            given(redisTemplate.delete(anyString())).willReturn(true);

            ipAccessService.createRule(request, 1L);

            assertThat(existing.getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteRule()")
    class DeleteRule {

        @Test
        @DisplayName("규칙을 비활성화(삭제)한다")
        void deleteRule() {
            IpAccessRule rule = IpAccessRule.builder()
                    .ipAddress("1.1.1.1")
                    .ruleType(IpRuleType.BLOCK)
                    .createdBy(1L)
                    .build();
            ReflectionTestUtils.setField(rule, "id", 5L);

            given(ipAccessRuleRepository.findById(5L)).willReturn(Optional.of(rule));
            given(redisTemplate.delete(anyString())).willReturn(true);

            ipAccessService.deleteRule(5L, 1L);

            assertThat(rule.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 규칙 삭제 시 IpRuleNotFoundException이 발생한다")
        void deleteRuleNotFound() {
            given(ipAccessRuleRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> ipAccessService.deleteRule(999L, 1L))
                    .isInstanceOf(IpRuleNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("autoBlock()")
    class AutoBlock {

        @Test
        @DisplayName("활성 BLOCK 규칙이 없으면 자동 차단 규칙을 생성한다")
        void autoBlockCreatesRule() {
            given(ipAccessRuleRepository.findByIpAddressAndRuleTypeAndIsActiveTrue("9.9.9.9", IpRuleType.BLOCK))
                    .willReturn(Optional.empty());

            IpAccessRule saved = IpAccessRule.builder()
                    .ipAddress("9.9.9.9")
                    .ruleType(IpRuleType.BLOCK)
                    .reason("브루트포스 감지")
                    .build();
            given(ipAccessRuleRepository.save(any())).willReturn(saved);
            given(redisTemplate.delete(anyString())).willReturn(true);

            ipAccessService.autoBlock("9.9.9.9", "브루트포스 감지");

            then(ipAccessRuleRepository).should().save(any());
        }

        @Test
        @DisplayName("이미 차단된 IP면 규칙을 추가 생성하지 않는다")
        void autoBlockSkipsIfAlreadyBlocked() {
            IpAccessRule existing = IpAccessRule.builder()
                    .ipAddress("9.9.9.9")
                    .ruleType(IpRuleType.BLOCK)
                    .createdBy(null)
                    .build();
            given(ipAccessRuleRepository.findByIpAddressAndRuleTypeAndIsActiveTrue("9.9.9.9", IpRuleType.BLOCK))
                    .willReturn(Optional.of(existing));

            ipAccessService.autoBlock("9.9.9.9", "재차단 시도");

            then(ipAccessRuleRepository).should(org.mockito.Mockito.never()).save(any());
        }
    }

    @Nested
    @DisplayName("getRules()")
    class GetRules {

        @Test
        @DisplayName("페이지 단위로 규칙 목록을 반환한다")
        void getRulesPageable() {
            IpAccessRule rule = IpAccessRule.builder()
                    .ipAddress("1.2.3.4")
                    .ruleType(IpRuleType.BLOCK)
                    .reason("테스트")
                    .createdBy(1L)
                    .build();
            ReflectionTestUtils.setField(rule, "id", 1L);

            Page<IpAccessRule> page = new PageImpl<>(List.of(rule), PageRequest.of(0, 20), 1);
            given(ipAccessRuleRepository.findAllByOrderByCreatedAtDesc(any())).willReturn(page);

            Page<IpRuleResponse> result = ipAccessService.getRules(0, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIpAddress()).isEqualTo("1.2.3.4");
        }
    }
}
