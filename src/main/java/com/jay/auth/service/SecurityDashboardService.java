package com.jay.auth.service;

import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.SecurityDashboardResponse;
import com.jay.auth.dto.response.SecurityDashboardResponse.SecurityActivity;
import com.jay.auth.dto.response.SecurityDashboardResponse.SecurityFactor;
import com.jay.auth.dto.response.TwoFactorStatusResponse;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.LoginHistoryRepository;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserPasskeyRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityDashboardService {

    private final UserRepository userRepository;
    private final UserSignInInfoRepository userSignInInfoRepository;
    private final UserChannelRepository userChannelRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserPasskeyRepository userPasskeyRepository;
    private final TotpService totpService;
    private final PasswordPolicyService passwordPolicyService;
    private final EncryptionService encryptionService;

    private static final int MAX_SCORE = 100;

    /**
     * 보안 대시보드 정보 조회
     */
    @Cacheable(value = "securityDashboard", key = "#userId")
    @Transactional(readOnly = true)
    public SecurityDashboardResponse getSecurityDashboard(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        List<SecurityFactor> factors = calculateSecurityFactors(userId, user);
        int rawScore = factors.stream().mapToInt(SecurityFactor::getScore).sum();
        int maxPossible = factors.stream().mapToInt(SecurityFactor::getMaxScore).sum();
        int totalScore = maxPossible > 0 ? (int) Math.round((double) rawScore / maxPossible * 100) : 0;
        String level = getSecurityLevel(totalScore);

        List<SecurityActivity> recentActivities = getRecentActivities(userId);
        List<String> recommendations = generateRecommendations(factors);

        return SecurityDashboardResponse.builder()
                .securityScore(totalScore)
                .securityLevel(level)
                .factors(factors)
                .recentActivities(recentActivities)
                .recommendations(recommendations)
                .build();
    }

    private List<SecurityFactor> calculateSecurityFactors(Long userId, User user) {
        List<SecurityFactor> factors = new ArrayList<>();

        factors.add(calculateTwoFactorScore(userId));

        UserSignInInfo signInInfo = userSignInInfoRepository.findByUserId(userId).orElse(null);
        SecurityFactor passwordFactor = calculatePasswordHealthScore(signInInfo);
        if (passwordFactor != null) {
            factors.add(passwordFactor);
        }

        factors.add(calculateRecoveryEmailScore(user));
        factors.add(calculateSocialLinkedScore(userId));
        factors.add(calculatePasskeyScore(userId));
        factors.add(calculateLoginMonitoringScore(userId));

        return factors;
    }

    // 1. 2FA 설정 (30점)
    private SecurityFactor calculateTwoFactorScore(Long userId) {
        TwoFactorStatusResponse twoFactorStatus = totpService.getTwoFactorStatus(userId);
        return SecurityFactor.builder()
                .name("2FA_ENABLED")
                .description("2단계 인증")
                .score(twoFactorStatus.isEnabled() ? 30 : 0)
                .maxScore(30)
                .enabled(twoFactorStatus.isEnabled())
                .build();
    }

    // 2. 비밀번호 강도/최신성 (25점) - 이메일 로그인 사용자만 해당, 소셜 전용 사용자는 null 반환
    private SecurityFactor calculatePasswordHealthScore(UserSignInInfo signInInfo) {
        if (signInInfo == null) {
            return null;
        }
        int passwordScore = calculatePasswordScore(signInInfo);
        return SecurityFactor.builder()
                .name("PASSWORD_HEALTH")
                .description("비밀번호 건강도")
                .score(passwordScore)
                .maxScore(25)
                .enabled(passwordScore >= 15)
                .build();
    }

    // 3. 복구 이메일 설정 (15점)
    private SecurityFactor calculateRecoveryEmailScore(User user) {
        boolean hasRecoveryEmail = user.getRecoveryEmailEnc() != null;
        return SecurityFactor.builder()
                .name("RECOVERY_EMAIL")
                .description("복구 이메일 설정")
                .score(hasRecoveryEmail ? 15 : 0)
                .maxScore(15)
                .enabled(hasRecoveryEmail)
                .build();
    }

    // 4. 소셜 계정 연결 (15점) - EMAIL 채널 제외, 순수 소셜 채널만 카운팅
    private SecurityFactor calculateSocialLinkedScore(Long userId) {
        long linkedSocialChannels = userChannelRepository.countByUserIdAndChannelCodeNot(userId, ChannelCode.EMAIL);
        int socialScore = linkedSocialChannels >= 2 ? 15 : (linkedSocialChannels == 1 ? 10 : 0);
        return SecurityFactor.builder()
                .name("SOCIAL_LINKED")
                .description("소셜 계정 연결")
                .score(socialScore)
                .maxScore(15)
                .enabled(linkedSocialChannels > 0)
                .build();
    }

    // 5. 패스키 등록 (15점)
    private SecurityFactor calculatePasskeyScore(Long userId) {
        boolean hasPasskeys = userPasskeyRepository.existsByUserId(userId);
        return SecurityFactor.builder()
                .name("PASSKEY_REGISTERED")
                .description("패스키 등록")
                .score(hasPasskeys ? 15 : 0)
                .maxScore(15)
                .enabled(hasPasskeys)
                .build();
    }

    // 6. 로그인 활동 모니터링 (15점)
    private SecurityFactor calculateLoginMonitoringScore(Long userId) {
        boolean recentLoginChecked = checkRecentLoginPattern(userId);
        return SecurityFactor.builder()
                .name("LOGIN_MONITORING")
                .description("로그인 활동 모니터링")
                .score(recentLoginChecked ? 15 : 10)
                .maxScore(15)
                .enabled(recentLoginChecked)
                .build();
    }

    private int calculatePasswordScore(UserSignInInfo signInInfo) {
        if (signInInfo == null) {
            return 0; // 소셜 로그인만 사용하는 경우
        }

        int score = 15; // 기본 점수

        // 비밀번호 만료 여부 확인
        if (passwordPolicyService.isPasswordExpired(signInInfo)) {
            score = 5;
        } else {
            int daysRemaining = passwordPolicyService.getDaysUntilExpiration(signInInfo);
            if (daysRemaining < 30) {
                score = 10;
            } else if (daysRemaining >= 60) {
                score = 25;
            } else {
                score = 20;
            }
        }

        return score;
    }

    private boolean checkRecentLoginPattern(Long userId) {
        List<LoginHistory> recentLogins = loginHistoryRepository.findRecentByUserId(userId, PageRequest.of(0, 10));
        // 최근 로그인 기록이 있고, 의심스러운 로그인이 없는 경우 true
        return !recentLogins.isEmpty() &&
                recentLogins.stream().allMatch(h -> Boolean.TRUE.equals(h.getIsSuccess()));
    }

    private String getSecurityLevel(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 70) return "GOOD";
        if (score >= 50) return "FAIR";
        if (score >= 30) return "WEAK";
        return "CRITICAL";
    }

    private List<SecurityActivity> getRecentActivities(Long userId) {
        List<LoginHistory> histories = loginHistoryRepository.findRecentByUserId(userId, PageRequest.of(0, 10));
        List<SecurityActivity> activities = new ArrayList<>();

        for (LoginHistory history : histories) {
            boolean isSuccess = Boolean.TRUE.equals(history.getIsSuccess());
            String type = isSuccess ? "LOGIN_SUCCESS" : "LOGIN_FAILURE";
            String description = isSuccess
                    ? history.getChannelCode().name() + " 로그인 성공"
                    : history.getChannelCode().name() + " 로그인 실패: " + history.getFailureReason();

            activities.add(SecurityActivity.builder()
                    .type(type)
                    .description(description)
                    .ipAddress(history.getIpAddress())
                    .location(history.getLocation())
                    .deviceInfo(history.getBrowser() + " / " + history.getOs())
                    .occurredAt(history.getCreatedAt())
                    .build());
        }

        return activities;
    }

    private List<String> generateRecommendations(List<SecurityFactor> factors) {
        List<String> recommendations = new ArrayList<>();

        for (SecurityFactor factor : factors) {
            if (!factor.isEnabled()) {
                switch (factor.getName()) {
                    case "2FA_ENABLED":
                        recommendations.add("2단계 인증을 활성화하여 계정 보안을 강화하세요.");
                        break;
                    case "PASSWORD_HEALTH":
                        if (factor.getScore() < 15) {
                            recommendations.add("비밀번호가 오래되었습니다. 새 비밀번호로 변경해주세요.");
                        }
                        break;
                    case "RECOVERY_EMAIL":
                        recommendations.add("복구 이메일을 설정하여 계정 복구를 쉽게 할 수 있습니다.");
                        break;
                    case "PASSKEY_REGISTERED":
                        recommendations.add("패스키를 등록하면 비밀번호 없이 안전하게 로그인할 수 있습니다.");
                        break;
                    case "SOCIAL_LINKED":
                        recommendations.add("소셜 계정을 연결하면 로그인 방법을 다양화할 수 있습니다.");
                        break;
                    default:
                        break;
                }
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("계정 보안이 잘 설정되어 있습니다!");
        }

        return recommendations;
    }
}
