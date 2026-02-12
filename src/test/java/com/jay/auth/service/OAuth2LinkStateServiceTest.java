package com.jay.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class OAuth2LinkStateServiceTest {

    @InjectMocks
    private OAuth2LinkStateService oAuth2LinkStateService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Nested
    @DisplayName("링크 상태 저장")
    class SaveLinkState {

        @Test
        @DisplayName("state와 userId가 Redis에 저장되어야 한다")
        void saveLinkStateSuccess() {
            // given
            String state = "random-state-123";
            Long userId = 1L;
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when
            oAuth2LinkStateService.saveLinkState(state, userId);

            // then
            verify(valueOperations).set(
                    "oauth2:link:" + state,
                    String.valueOf(userId),
                    Duration.ofMinutes(10)
            );
        }
    }

    @Nested
    @DisplayName("링크 사용자 ID 조회")
    class GetLinkUserId {

        @Test
        @DisplayName("저장된 state에 대해 userId를 반환해야 한다")
        void getLinkUserIdSuccess() {
            // given
            String state = "random-state-123";
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("oauth2:link:" + state)).willReturn("1");

            // when
            Long result = oAuth2LinkStateService.getLinkUserId(state);

            // then
            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("저장되지 않은 state는 null을 반환해야 한다")
        void getLinkUserIdReturnsNullWhenNotFound() {
            // given
            String state = "unknown-state";
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("oauth2:link:" + state)).willReturn(null);

            // when
            Long result = oAuth2LinkStateService.getLinkUserId(state);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null state는 null을 반환해야 한다")
        void getLinkUserIdReturnsNullForNullState() {
            // when
            Long result = oAuth2LinkStateService.getLinkUserId(null);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("링크 상태 제거")
    class RemoveLinkState {

        @Test
        @DisplayName("state에 해당하는 키가 Redis에서 삭제되어야 한다")
        void removeLinkStateSuccess() {
            // given
            String state = "random-state-123";

            // when
            oAuth2LinkStateService.removeLinkState(state);

            // then
            verify(redisTemplate).delete("oauth2:link:" + state);
        }

        @Test
        @DisplayName("null state는 삭제를 수행하지 않아야 한다")
        void removeLinkStateWithNullDoesNothing() {
            // when
            oAuth2LinkStateService.removeLinkState(null);

            // then
            verify(redisTemplate, never()).delete(anyString());
        }
    }
}
