package com.jay.auth.controller;

import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.EmailSignUpRequest;
import com.jay.auth.dto.request.SendVerificationRequest;
import com.jay.auth.dto.request.VerifyEmailRequest;
import com.jay.auth.dto.response.SignUpResponse;
import com.jay.auth.dto.response.VerificationResponse;
import com.jay.auth.exception.DuplicateEmailException;
import com.jay.auth.service.AuthService;
import com.jay.auth.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "이메일 인증 코드 발송", description = "회원가입을 위한 이메일 인증 코드를 발송합니다")
    @PostMapping("/email/send-verification")
    public ResponseEntity<VerificationResponse> sendVerification(
            @Valid @RequestBody SendVerificationRequest request) {

        // 이메일 중복 체크
        if (authService.isEmailExists(request.getEmail())) {
            throw new DuplicateEmailException();
        }

        String tokenId = emailVerificationService.sendVerificationCode(
                request.getEmail(),
                VerificationType.SIGNUP
        );

        return ResponseEntity.ok(VerificationResponse.sent(
                tokenId,
                emailVerificationService.getExpirationMinutes() * 60
        ));
    }

    @Operation(summary = "이메일 인증 코드 확인", description = "발송된 인증 코드를 확인합니다")
    @PostMapping("/email/verify")
    public ResponseEntity<VerificationResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        emailVerificationService.verifyCode(
                request.getEmail(),
                request.getCode(),
                VerificationType.SIGNUP
        );

        return ResponseEntity.ok(VerificationResponse.verified());
    }

    @Operation(summary = "이메일 회원가입", description = "이메일 인증 완료 후 회원가입을 진행합니다")
    @PostMapping("/email/signup")
    public ResponseEntity<SignUpResponse> signUp(
            @Valid @RequestBody EmailSignUpRequest request) {

        SignUpResponse response = authService.signUpWithEmail(request);

        return ResponseEntity.ok(response);
    }
}
