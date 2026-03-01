package com.jay.auth.controller;

import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.SendVerificationRequest;
import com.jay.auth.dto.request.VerifyEmailRequest;
import com.jay.auth.dto.response.VerificationResponse;
import com.jay.auth.exception.DuplicateEmailException;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.dto.request.PostLoginVerifyRequest;
import com.jay.auth.service.AuthService;
import com.jay.auth.service.EmailVerificationService;
import com.jay.auth.service.PostLoginVerificationService;
import com.jay.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Email Verification", description = "이메일 인증 API")
@RestController
@RequestMapping("/api/v1/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PostLoginVerificationService postLoginVerificationService;
    private final UserService userService;

    @Operation(summary = "이메일 인증 코드 발송", description = "회원가입, 복구이메일 등록, 비밀번호 재설정을 위한 이메일 인증 코드를 발송합니다")
    @PostMapping("/send-verification")
    public ResponseEntity<VerificationResponse> sendVerification(
            @Valid @RequestBody SendVerificationRequest request) {

        VerificationType type = request.getType();

        // 타입별 검증 로직
        switch (type) {
            case SIGNUP -> {
                // 회원가입: 이메일 중복 체크
                if (authService.isEmailExists(request.getEmail())) {
                    throw new DuplicateEmailException();
                }
            }
            case PASSWORD_RESET -> {
                // 비밀번호 재설정: 복구 이메일로 등록된 사용자가 있는지 확인
                if (!userService.existsByRecoveryEmail(request.getEmail())) {
                    throw UserNotFoundException.recoveryEmailNotFound();
                }
            }
            case EMAIL_CHANGE -> {
                // 이메일 변경: 소셜 로그인 채널 이메일은 복구 이메일로 사용 불가
                if (authService.isSocialChannelEmail(request.getEmail())) {
                    throw new InvalidVerificationException("소셜 로그인에 사용 중인 이메일은 복구 이메일로 등록할 수 없습니다");
                }
            }
        }

        String tokenId = emailVerificationService.sendVerificationCode(
                request.getEmail(),
                type
        );

        return ResponseEntity.ok(VerificationResponse.sent(
                tokenId,
                emailVerificationService.getExpirationMinutes() * 60
        ));
    }

    @Operation(summary = "이메일 인증 코드 확인", description = "발송된 인증 코드를 확인합니다")
    @PostMapping("/verify")
    public ResponseEntity<VerificationResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        String tokenId = emailVerificationService.verifyCode(
                request.getEmail(),
                request.getCode(),
                request.getType()
        );

        return ResponseEntity.ok(VerificationResponse.verified(tokenId));
    }

    @Operation(summary = "로그인 후 이메일 재인증", description = "새 기기 로그인 감지 시 이메일 인증 코드를 확인합니다")
    @PostMapping("/verify-login")
    public ResponseEntity<VerificationResponse> verifyLogin(
            @Valid @RequestBody PostLoginVerifyRequest request) {

        String tokenId = postLoginVerificationService.verifyCode(
                request.getEmail(),
                request.getCode()
        );

        return ResponseEntity.ok(VerificationResponse.verified(tokenId));
    }
}
