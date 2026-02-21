package com.jay.auth.service;

import com.jay.auth.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ClaudeAiReplyServiceTest {

    @InjectMocks
    private ClaudeAiReplyService claudeAiReplyService;

    @Mock
    private AppProperties appProperties;

    @Mock
    private RestTemplate restTemplate;

    private AppProperties.Ai.Claude claudeConfig;

    @BeforeEach
    void setUp() {
        AppProperties.Ai ai = new AppProperties.Ai();
        claudeConfig = ai.getClaude();
        claudeConfig.setApiKey("test-api-key");
        claudeConfig.setModel("claude-sonnet-4-5-20250929");
        claudeConfig.setMaxTokens(1024);

        AppProperties.Ai mockAi = new AppProperties.Ai();
        mockAi.getClaude().setApiKey("test-api-key");
        mockAi.getClaude().setModel("claude-sonnet-4-5-20250929");
        mockAi.getClaude().setMaxTokens(1024);

        given(appProperties.getAi()).willReturn(mockAi);

        injectRestTemplate();
    }

    @Nested
    @DisplayName("AI 응답 생성 성공")
    class GenerateReplySuccess {

        @Test
        @DisplayName("정상 응답 시 AI 생성 텍스트를 반환해야 한다")
        void generateReplyReturnsText() {
            // given
            Map<String, Object> responseBody = Map.of(
                    "content", List.of(Map.of("text", "안녕하세요, 고객님. 문의해 주셔서 감사합니다."))
            );
            given(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                    .willReturn(ResponseEntity.ok(responseBody));

            // when
            String result = claudeAiReplyService.generateReply("계정 문의", "로그인이 안 됩니다", "ACCOUNT");

            // then
            assertThat(result).isEqualTo("안녕하세요, 고객님. 문의해 주셔서 감사합니다.");
        }

        @Test
        @DisplayName("카테고리/제목/내용이 다양해도 응답을 반환해야 한다")
        void generateReplyWithDifferentCategories() {
            // given
            Map<String, Object> responseBody = Map.of(
                    "content", List.of(Map.of("text", "보안 관련 문의 답변입니다."))
            );
            given(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                    .willReturn(ResponseEntity.ok(responseBody));

            // when
            String result = claudeAiReplyService.generateReply("보안 문의", "2FA 설정 방법", "SECURITY");

            // then
            assertThat(result).isEqualTo("보안 관련 문의 답변입니다.");
        }
    }

    @Nested
    @DisplayName("API 실패 시 폴백 응답")
    class GenerateReplyFallback {

        @Test
        @DisplayName("RestTemplate 예외 발생 시 폴백 메시지를 반환해야 한다")
        void generateReplyFallbackOnException() {
            // given
            given(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                    .willThrow(new RestClientException("Connection refused"));

            // when
            String result = claudeAiReplyService.generateReply("제목", "내용", "OTHER");

            // then
            assertThat(result).contains("고객센터");
            assertThat(result).contains("담당자");
        }

        @Test
        @DisplayName("API가 비성공 상태코드를 반환하면 폴백 메시지를 반환해야 한다")
        void generateReplyFallbackOnBadStatus() {
            // given
            given(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                    .willReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

            // when
            String result = claudeAiReplyService.generateReply("제목", "내용", "LOGIN");

            // then
            assertThat(result).contains("고객센터");
        }

        @Test
        @DisplayName("응답 body가 null이면 폴백 메시지를 반환해야 한다")
        void generateReplyFallbackOnNullBody() {
            // given
            given(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                    .willReturn(ResponseEntity.ok(null));

            // when
            String result = claudeAiReplyService.generateReply("제목", "내용", "ACCOUNT");

            // then
            assertThat(result).contains("고객센터");
        }

        @Test
        @DisplayName("content 블록이 비어있으면 폴백 메시지를 반환해야 한다")
        void generateReplyFallbackOnEmptyContentBlocks() {
            // given
            Map<String, Object> responseBody = Map.of("content", List.of());
            given(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                    .willReturn(ResponseEntity.ok(responseBody));

            // when
            String result = claudeAiReplyService.generateReply("제목", "내용", "ACCOUNT");

            // then
            assertThat(result).contains("고객센터");
        }
    }

    private void injectRestTemplate() {
        try {
            java.lang.reflect.Field field = ClaudeAiReplyService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(claudeAiReplyService, restTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
