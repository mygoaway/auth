package com.jay.auth.service;

import com.jay.auth.repository.EmailVerificationRepository;
import com.jay.auth.repository.PhoneVerificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerificationCleanupSchedulerTest {

    @InjectMocks
    private VerificationCleanupScheduler scheduler;

    @Mock
    private PhoneVerificationRepository phoneVerificationRepository;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Test
    @DisplayName("만료된 인증 레코드가 정리되어야 한다")
    void cleanupExpiredVerifications() {
        // given
        given(phoneVerificationRepository.deleteExpiredVerifications(any(LocalDateTime.class))).willReturn(3);
        given(emailVerificationRepository.deleteExpired(any(LocalDateTime.class))).willReturn(5);

        // when
        scheduler.cleanupExpiredVerifications();

        // then
        verify(phoneVerificationRepository).deleteExpiredVerifications(any(LocalDateTime.class));
        verify(emailVerificationRepository).deleteExpired(any(LocalDateTime.class));
    }
}
