package com.jay.auth.controller;

import com.jay.auth.dto.request.EmailLoginRequest;
import com.jay.auth.dto.request.EmailSignUpRequest;
import com.jay.auth.dto.response.LoginResponse;
import com.jay.auth.dto.response.SignUpResponse;
import com.jay.auth.service.AuthService;
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

    @Operation(summary = "이메일 회원가입", description = "이메일 인증 완료 후 회원가입을 진행합니다")
    @PostMapping("/email/signup")
    public ResponseEntity<SignUpResponse> signUp(
            @Valid @RequestBody EmailSignUpRequest request) {

        SignUpResponse response = authService.signUpWithEmail(request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "이메일 로그인", description = "이메일과 비밀번호로 로그인합니다")
    @PostMapping("/email/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody EmailLoginRequest request) {

        LoginResponse response = authService.loginWithEmail(request);

        return ResponseEntity.ok(response);
    }
}
