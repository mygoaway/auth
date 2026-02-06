package com.jay.auth.controller;

import com.jay.auth.dto.request.SendPhoneVerificationRequest;
import com.jay.auth.dto.request.VerifyPhoneRequest;
import com.jay.auth.dto.response.PhoneVerificationResponse;
import com.jay.auth.service.PhoneVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Phone Verification", description = "핸드폰 인증 API")
@RestController
@RequestMapping("/api/v1/phone")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    @Operation(summary = "핸드폰 인증 코드 발송", description = "핸드폰 인증 코드를 발송합니다 (실제 SMS 대신 로그로 출력)")
    @PostMapping("/send-verification")
    public ResponseEntity<PhoneVerificationResponse> sendVerification(
            @Valid @RequestBody SendPhoneVerificationRequest request) {

        String tokenId = phoneVerificationService.sendVerificationCode(request.getPhone());

        return ResponseEntity.ok(PhoneVerificationResponse.sent(
                tokenId,
                phoneVerificationService.getExpirationMinutes() * 60
        ));
    }

    @Operation(summary = "핸드폰 인증 코드 확인", description = "발송된 인증 코드를 확인합니다")
    @PostMapping("/verify")
    public ResponseEntity<PhoneVerificationResponse> verifyPhone(
            @Valid @RequestBody VerifyPhoneRequest request) {

        String tokenId = phoneVerificationService.verifyCode(request.getPhone(), request.getCode());

        return ResponseEntity.ok(PhoneVerificationResponse.verified(tokenId));
    }
}
