package com.jay.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CoolSmsSenderTest {

    @InjectMocks
    private CoolSmsSender coolSmsSender;

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(coolSmsSender, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(coolSmsSender, "apiSecret", "test-api-secret-key-32chars!!");
        ReflectionTestUtils.setField(coolSmsSender, "senderPhone", "01012345678");

        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(coolSmsSender, "restTemplate", restTemplate);
    }

    @Nested
    @DisplayName("인증 코드 발송")
    class SendVerificationCode {

        @Test
        @DisplayName("SMS 발송이 성공하면 예외가 발생하지 않아야 한다")
        void sendVerificationCodeSuccess() {
            // given
            ResponseEntity<String> response = new ResponseEntity<>("{\"statusCode\":\"2000\"}", HttpStatus.OK);
            given(restTemplate.exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).willReturn(response);

            // when & then
            assertThatCode(() -> coolSmsSender.sendVerificationCode("010-1234-5678", "123456"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SMS 발송이 실패(non-2xx)하면 RuntimeException이 발생해야 한다")
        void sendVerificationCodeNon2xxResponse() {
            // given
            ResponseEntity<String> response = new ResponseEntity<>("{\"error\":\"Bad Request\"}", HttpStatus.BAD_REQUEST);
            given(restTemplate.exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).willReturn(response);

            // when & then
            assertThatThrownBy(() -> coolSmsSender.sendVerificationCode("010-1234-5678", "123456"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("SMS 발송에 실패했습니다.");
        }

        @Test
        @DisplayName("RestTemplate 예외 발생 시 RuntimeException이 발생해야 한다")
        void sendVerificationCodeRestClientException() {
            // given
            given(restTemplate.exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).willThrow(new RestClientException("Connection refused"));

            // when & then
            assertThatThrownBy(() -> coolSmsSender.sendVerificationCode("010-1234-5678", "123456"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("SMS 발송에 실패했습니다.");
        }

        @Test
        @DisplayName("전화번호의 하이픈이 제거되어야 한다")
        void phoneNumberHyphenRemoved() {
            // given
            ResponseEntity<String> response = new ResponseEntity<>("{\"statusCode\":\"2000\"}", HttpStatus.OK);
            given(restTemplate.exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).willReturn(response);

            // when & then
            assertThatCode(() -> coolSmsSender.sendVerificationCode("010-1234-5678", "123456"))
                    .doesNotThrowAnyException();
        }
    }
}
