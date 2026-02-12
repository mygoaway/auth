package com.jay.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenStoreTest {

    @InjectMocks
    private TokenStore tokenStore;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
    }

    @Nested
    @DisplayName("Refresh Token 저장")
    class SaveRefreshToken {

        @Test
        @DisplayName("Refresh Token이 Redis에 정상 저장되어야 한다")
        void shouldSaveRefreshToken() {
            // given
            Long userId = 1L;
            String tokenId = "token-id-1";
            String refreshToken = "refresh-token-value";
            long expirationMs = 1209600000L;

            // when
            tokenStore.saveRefreshToken(userId, tokenId, refreshToken, expirationMs);

            // then
            verify(valueOperations).set(
                    "refresh:1:token-id-1",
                    refreshToken,
                    expirationMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    @Nested
    @DisplayName("Refresh Token 조회")
    class GetRefreshToken {

        @Test
        @DisplayName("저장된 Refresh Token을 정상 조회해야 한다")
        void shouldReturnRefreshToken() {
            // given
            given(valueOperations.get("refresh:1:token-id-1")).willReturn("refresh-token-value");

            // when
            String result = tokenStore.getRefreshToken(1L, "token-id-1");

            // then
            assertThat(result).isEqualTo("refresh-token-value");
        }

        @Test
        @DisplayName("존재하지 않는 Refresh Token은 null을 반환해야 한다")
        void shouldReturnNullForMissingToken() {
            // given
            given(valueOperations.get("refresh:1:nonexistent")).willReturn(null);

            // when
            String result = tokenStore.getRefreshToken(1L, "nonexistent");

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Refresh Token 존재 여부 확인")
    class ExistsRefreshToken {

        @Test
        @DisplayName("존재하는 Refresh Token은 true를 반환해야 한다")
        void shouldReturnTrueForExistingToken() {
            // given
            given(redisTemplate.hasKey("refresh:1:token-id-1")).willReturn(true);

            // when
            boolean result = tokenStore.existsRefreshToken(1L, "token-id-1");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 Refresh Token은 false를 반환해야 한다")
        void shouldReturnFalseForMissingToken() {
            // given
            given(redisTemplate.hasKey("refresh:1:nonexistent")).willReturn(false);

            // when
            boolean result = tokenStore.existsRefreshToken(1L, "nonexistent");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Refresh Token 삭제")
    class DeleteRefreshToken {

        @Test
        @DisplayName("Refresh Token이 Redis에서 삭제되어야 한다")
        void shouldDeleteRefreshToken() {
            // when
            tokenStore.deleteRefreshToken(1L, "token-id-1");

            // then
            verify(redisTemplate).delete("refresh:1:token-id-1");
        }
    }

    @Nested
    @DisplayName("모든 Refresh Token 삭제")
    class DeleteAllRefreshTokens {

        @Test
        @DisplayName("사용자의 모든 Refresh Token과 세션이 삭제되어야 한다")
        void shouldDeleteAllRefreshTokensAndSessions() {
            // given
            Set<String> tokenKeys = new HashSet<>(Arrays.asList("refresh:1:t1", "refresh:1:t2"));
            Set<String> sessionKeys = new HashSet<>(Arrays.asList("session:1:t1", "session:1:t2"));

            given(redisTemplate.keys("refresh:1:*")).willReturn(tokenKeys);
            given(redisTemplate.keys("session:1:*")).willReturn(sessionKeys);

            // when
            tokenStore.deleteAllRefreshTokens(1L);

            // then
            verify(redisTemplate).delete(tokenKeys);
            verify(redisTemplate).delete(sessionKeys);
        }

        @Test
        @DisplayName("삭제할 토큰이 없으면 delete를 호출하지 않아야 한다")
        void shouldNotDeleteWhenNoTokensExist() {
            // given
            given(redisTemplate.keys("refresh:1:*")).willReturn(Collections.emptySet());
            given(redisTemplate.keys("session:1:*")).willReturn(Collections.emptySet());

            // when
            tokenStore.deleteAllRefreshTokens(1L);

            // then
            verify(redisTemplate, never()).delete(anyCollection());
        }
    }

    @Nested
    @DisplayName("Access Token 블랙리스트 등록")
    class AddToBlacklist {

        @Test
        @DisplayName("Access Token이 블랙리스트에 등록되어야 한다")
        void shouldAddToBlacklist() {
            // given
            String tokenId = "access-token-id";
            long remainingMs = 900000L;

            // when
            tokenStore.addToBlacklist(tokenId, remainingMs);

            // then
            verify(valueOperations).set(
                    "blacklist:access-token-id",
                    "1",
                    remainingMs,
                    TimeUnit.MILLISECONDS
            );
        }

        @Test
        @DisplayName("만료 시간이 0 이하이면 블랙리스트에 등록하지 않아야 한다")
        void shouldNotAddToBlacklistWhenExpirationIsZeroOrNegative() {
            // when
            tokenStore.addToBlacklist("token-id", 0L);

            // then
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    @DisplayName("Access Token 블랙리스트 확인")
    class IsBlacklisted {

        @Test
        @DisplayName("블랙리스트에 등록된 토큰은 true를 반환해야 한다")
        void shouldReturnTrueForBlacklistedToken() {
            // given
            given(redisTemplate.hasKey("blacklist:token-id")).willReturn(true);

            // when
            boolean result = tokenStore.isBlacklisted("token-id");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("블랙리스트에 없는 토큰은 false를 반환해야 한다")
        void shouldReturnFalseForNonBlacklistedToken() {
            // given
            given(redisTemplate.hasKey("blacklist:token-id")).willReturn(false);

            // when
            boolean result = tokenStore.isBlacklisted("token-id");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("활성 세션 조회")
    class GetAllSessions {

        @Test
        @DisplayName("사용자의 모든 활성 세션을 조회해야 한다")
        void shouldReturnAllSessions() {
            // given
            Set<String> keys = new LinkedHashSet<>(Arrays.asList("session:1:t1", "session:1:t2"));
            given(redisTemplate.keys("session:1:*")).willReturn(keys);

            Map<Object, Object> sessionData1 = new HashMap<>();
            sessionData1.put("deviceType", "Desktop");
            sessionData1.put("browser", "Chrome");
            sessionData1.put("os", "Windows");
            sessionData1.put("ipAddress", "127.0.0.1");
            sessionData1.put("location", "Seoul");
            sessionData1.put("lastActivity", "2025-01-15T10:30:00");

            Map<Object, Object> sessionData2 = new HashMap<>();
            sessionData2.put("deviceType", "Mobile");
            sessionData2.put("browser", "Safari");
            sessionData2.put("os", "iOS");
            sessionData2.put("ipAddress", "192.168.1.1");
            sessionData2.put("location", "Busan");
            sessionData2.put("lastActivity", "2025-01-15T11:00:00");

            given(hashOperations.entries("session:1:t1")).willReturn(sessionData1);
            given(hashOperations.entries("session:1:t2")).willReturn(sessionData2);

            // when
            List<Map<String, String>> sessions = tokenStore.getAllSessions(1L);

            // then
            assertThat(sessions).hasSize(2);
            // Sorted by lastActivity descending, so t2 (11:00) comes first
            assertThat(sessions.get(0).get("sessionId")).isEqualTo("t2");
            assertThat(sessions.get(0).get("deviceType")).isEqualTo("Mobile");
            assertThat(sessions.get(1).get("sessionId")).isEqualTo("t1");
            assertThat(sessions.get(1).get("deviceType")).isEqualTo("Desktop");
        }

        @Test
        @DisplayName("세션이 없으면 빈 리스트를 반환해야 한다")
        void shouldReturnEmptyListWhenNoSessions() {
            // given
            given(redisTemplate.keys("session:1:*")).willReturn(null);

            // when
            List<Map<String, String>> sessions = tokenStore.getAllSessions(1L);

            // then
            assertThat(sessions).isEmpty();
        }
    }

    @Nested
    @DisplayName("세션 삭제")
    class RevokeSession {

        @Test
        @DisplayName("특정 세션이 삭제되어야 한다")
        void shouldRevokeSession() {
            // when
            tokenStore.revokeSession(1L, "token-id-1");

            // then
            verify(redisTemplate).delete("refresh:1:token-id-1");
            verify(redisTemplate).delete("session:1:token-id-1");
        }
    }

    @Nested
    @DisplayName("세션 정보와 함께 Refresh Token 저장")
    class SaveRefreshTokenWithSession {

        @Test
        @DisplayName("세션 정보와 함께 Refresh Token이 저장되어야 한다")
        void shouldSaveRefreshTokenWithSession() {
            // given
            Long userId = 1L;
            String tokenId = "token-id-1";
            String refreshToken = "refresh-token";
            long expirationMs = 1209600000L;
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows", "127.0.0.1", "Seoul");

            // when
            tokenStore.saveRefreshTokenWithSession(userId, tokenId, refreshToken, expirationMs, sessionInfo);

            // then
            verify(valueOperations).set("refresh:1:token-id-1", refreshToken, expirationMs, TimeUnit.MILLISECONDS);
            verify(hashOperations).putAll(eq("session:1:token-id-1"), anyMap());
            verify(redisTemplate).expire("session:1:token-id-1", expirationMs, TimeUnit.MILLISECONDS);
        }
    }

    @Nested
    @DisplayName("세션 활동 시간 갱신")
    class UpdateSessionActivity {

        @Test
        @DisplayName("세션이 존재하면 마지막 활동 시간이 갱신되어야 한다")
        void shouldUpdateLastActivity() {
            // given
            given(redisTemplate.hasKey("session:1:token-id-1")).willReturn(true);

            // when
            tokenStore.updateSessionActivity(1L, "token-id-1");

            // then
            verify(hashOperations).put(eq("session:1:token-id-1"), eq("lastActivity"), anyString());
        }

        @Test
        @DisplayName("세션이 존재하지 않으면 갱신하지 않아야 한다")
        void shouldNotUpdateWhenSessionDoesNotExist() {
            // given
            given(redisTemplate.hasKey("session:1:nonexistent")).willReturn(false);

            // when
            tokenStore.updateSessionActivity(1L, "nonexistent");

            // then
            verify(hashOperations, never()).put(anyString(), anyString(), anyString());
        }
    }
}
