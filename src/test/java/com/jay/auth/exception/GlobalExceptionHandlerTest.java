package com.jay.auth.exception;

import com.jay.auth.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler 테스트")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleBusinessException 메서드")
    class HandleBusinessException {

        @Test
        @DisplayName("BusinessException 발생 시 해당 HTTP 상태코드와 에러 정보를 반환한다")
        void shouldReturnCorrectStatusAndErrorInfo() {
            // given
            DuplicateEmailException exception = new DuplicateEmailException();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError().getCode()).isEqualTo("AUTH001");
            assertThat(response.getBody().getError().getMessage()).isEqualTo("이미 가입된 이메일입니다");
        }

        @Test
        @DisplayName("AuthenticationException 발생 시 UNAUTHORIZED 상태코드를 반환한다")
        void shouldReturnUnauthorizedForAuthenticationException() {
            // given
            AuthenticationException exception = AuthenticationException.invalidCredentials();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError().getCode()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("AccountLinkingException 발생 시 해당 상태코드를 반환한다")
        void shouldReturnCorrectStatusForAccountLinkingException() {
            // given
            AccountLinkingException exception = AccountLinkingException.alreadyLinkedToAnotherUser();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError().getCode()).isEqualTo("ACCOUNT_ALREADY_LINKED_TO_ANOTHER");
        }
    }

    @Nested
    @DisplayName("handleValidationException 메서드")
    class HandleValidationException {

        @Test
        @DisplayName("유효성 검증 실패 시 BAD_REQUEST와 필드 에러 메시지를 반환한다")
        void shouldReturnBadRequestWithFieldErrors() {
            // given
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", "email", "이메일 형식이 올바르지 않습니다"));
            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException(null, bindingResult);

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError().getCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().getError().getMessage()).isEqualTo("이메일 형식이 올바르지 않습니다");
        }

        @Test
        @DisplayName("여러 필드 에러가 있을 경우 쉼표로 구분된 메시지를 반환한다")
        void shouldJoinMultipleFieldErrors() {
            // given
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", "email", "이메일은 필수입니다"));
            bindingResult.addError(new FieldError("request", "password", "비밀번호는 필수입니다"));
            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException(null, bindingResult);

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

            // then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError().getMessage()).contains("이메일은 필수입니다");
            assertThat(response.getBody().getError().getMessage()).contains("비밀번호는 필수입니다");
            assertThat(response.getBody().getError().getMessage()).contains(", ");
        }
    }

    @Nested
    @DisplayName("handleIllegalArgumentException 메서드")
    class HandleIllegalArgumentException {

        @Test
        @DisplayName("IllegalArgumentException 발생 시 BAD_REQUEST를 반환한다")
        void shouldReturnBadRequest() {
            // given
            IllegalArgumentException exception = new IllegalArgumentException("잘못된 인자입니다");

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError().getCode()).isEqualTo("BAD_REQUEST");
            assertThat(response.getBody().getError().getMessage()).isEqualTo("잘못된 인자입니다");
        }
    }

    @Nested
    @DisplayName("handleRateLimitException 메서드")
    class HandleRateLimitException {

        @Test
        @DisplayName("RateLimitException 발생 시 429 상태코드와 Retry-After 헤더를 반환한다")
        void shouldReturnTooManyRequestsWithRetryAfter() {
            // given
            RateLimitException exception = new RateLimitException("요청이 너무 많습니다", 30);

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleRateLimitException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError().getCode()).isEqualTo("RATE_LIMITED");
            assertThat(response.getBody().getError().getMessage()).isEqualTo("요청이 너무 많습니다");
        }

        @Test
        @DisplayName("기본 생성자로 생성된 RateLimitException도 정상 처리한다")
        void shouldHandleDefaultConstructor() {
            // given
            RateLimitException exception = new RateLimitException(60);

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleRateLimitException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError().getMessage())
                    .isEqualTo("너무 많은 시도가 있었습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Nested
    @DisplayName("handlePasswordPolicyException 메서드")
    class HandlePasswordPolicyException {

        @Test
        @DisplayName("비밀번호 만료 예외 시 BAD_REQUEST와 에러코드를 반환한다")
        void shouldReturnBadRequestForExpiredPassword() {
            // given
            PasswordPolicyException exception = PasswordPolicyException.expired();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handlePasswordPolicyException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError().getCode()).isEqualTo("PASSWORD_EXPIRED");
            assertThat(response.getBody().getError().getMessage())
                    .isEqualTo("비밀번호가 만료되었습니다. 새 비밀번호로 변경해주세요.");
        }

        @Test
        @DisplayName("비밀번호 재사용 예외를 정상 처리한다")
        void shouldHandlePasswordReused() {
            // given
            PasswordPolicyException exception = PasswordPolicyException.reused();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handlePasswordPolicyException(exception);

            // then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError().getCode()).isEqualTo("PASSWORD_REUSED");
        }

        @Test
        @DisplayName("현재 비밀번호와 동일한 경우 예외를 정상 처리한다")
        void shouldHandlePasswordSameAsCurrent() {
            // given
            PasswordPolicyException exception = PasswordPolicyException.sameAsCurrent();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handlePasswordPolicyException(exception);

            // then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError().getCode()).isEqualTo("PASSWORD_SAME_AS_CURRENT");
        }
    }

    @Nested
    @DisplayName("handleTwoFactorException 메서드")
    class HandleTwoFactorException {

        @Test
        @DisplayName("TwoFactorException 발생 시 BAD_REQUEST와 에러 정보를 반환한다")
        void shouldReturnBadRequestForTwoFactorException() {
            // given
            TwoFactorException exception = TwoFactorException.notSetup();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleTwoFactorException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("2FA 필수 예외 시 에러 정보를 반환한다")
        void shouldHandleTwoFactorRequired() {
            // given
            TwoFactorException exception = TwoFactorException.required();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleTwoFactorException(exception);

            // then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError()).isNotNull();
        }

        @Test
        @DisplayName("잘못된 인증 코드 예외를 정상 처리한다")
        void shouldHandleInvalidCode() {
            // given
            TwoFactorException exception = TwoFactorException.invalidCode();

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleTwoFactorException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("handleException 메서드")
    class HandleGenericException {

        @Test
        @DisplayName("예상치 못한 예외 발생 시 500 상태코드를 반환한다")
        void shouldReturnInternalServerError() {
            // given
            Exception exception = new RuntimeException("서버 내부 오류");

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError().getCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().getError().getMessage()).isEqualTo("서버 오류가 발생했습니다");
        }

        @Test
        @DisplayName("NullPointerException도 generic 핸들러에서 처리된다")
        void shouldHandleNullPointerException() {
            // given
            Exception exception = new NullPointerException("null 참조");

            // when
            ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError().getCode()).isEqualTo("INTERNAL_ERROR");
        }
    }
}
