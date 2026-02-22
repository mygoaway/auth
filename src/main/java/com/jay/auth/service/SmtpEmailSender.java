package com.jay.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    private static final String FROM_ADDRESS = "noreply@authly.com";

    @Override
    public void sendVerificationCode(String to, String code) {
        Context context = new Context();
        context.setVariable("title", "이메일 인증");
        context.setVariable("message", "아래 인증 코드를 입력해주세요.");
        context.setVariable("code", code);
        context.setVariable("expiresMinutes", 10);

        String html = templateEngine.process("email/verification-code", context);
        sendHtmlEmail(to, "[Authly] 이메일 인증 코드", html);
    }

    @Override
    public void sendNewDeviceLoginAlert(String to, String deviceInfo, String ipAddress, String location, String loginTime) {
        List<Map<String, String>> details = List.of(
                Map.of("key", "기기", "value", deviceInfo),
                Map.of("key", "IP 주소", "value", ipAddress),
                Map.of("key", "위치", "value", location != null ? location : "알 수 없음"),
                Map.of("key", "시간", "value", loginTime)
        );

        Context context = new Context();
        context.setVariable("alertTitle", "새 기기에서 로그인");
        context.setVariable("alertMessage", "회원님의 계정이 새로운 기기에서 로그인되었습니다.");
        context.setVariable("details", details);

        String html = templateEngine.process("email/security-alert", context);
        sendHtmlEmail(to, "[Authly] 새 기기 로그인 알림", html);
    }

    @Override
    public void sendPasswordChangedAlert(String to, String changeTime) {
        List<Map<String, String>> details = List.of(
                Map.of("key", "변경 시간", "value", changeTime)
        );

        Context context = new Context();
        context.setVariable("alertTitle", "비밀번호 변경 완료");
        context.setVariable("alertMessage", "회원님의 비밀번호가 변경되었습니다.");
        context.setVariable("details", details);

        String html = templateEngine.process("email/security-alert", context);
        sendHtmlEmail(to, "[Authly] 비밀번호 변경 알림", html);
    }

    @Override
    public void sendAccountLinkedAlert(String to, String channelName, String linkedTime) {
        List<Map<String, String>> details = List.of(
                Map.of("key", "연동 채널", "value", channelName),
                Map.of("key", "연동 시간", "value", linkedTime)
        );

        Context context = new Context();
        context.setVariable("alertTitle", "계정 연동 완료");
        context.setVariable("alertMessage", channelName + " 계정이 연동되었습니다.");
        context.setVariable("details", details);

        String html = templateEngine.process("email/security-alert", context);
        sendHtmlEmail(to, "[Authly] 계정 연동 알림", html);
    }

    @Override
    public void sendAccountUnlinkedAlert(String to, String channelName, String unlinkedTime) {
        List<Map<String, String>> details = List.of(
                Map.of("key", "해제 채널", "value", channelName),
                Map.of("key", "해제 시간", "value", unlinkedTime)
        );

        Context context = new Context();
        context.setVariable("alertTitle", "계정 연동 해제");
        context.setVariable("alertMessage", channelName + " 계정 연동이 해제되었습니다.");
        context.setVariable("details", details);

        String html = templateEngine.process("email/security-alert", context);
        sendHtmlEmail(to, "[Authly] 계정 연동 해제 알림", html);
    }

    @Override
    public void sendPasskeyRegisteredAlert(String to, String deviceName, String registeredTime) {
        List<Map<String, String>> details = List.of(
                Map.of("key", "기기 이름", "value", deviceName),
                Map.of("key", "등록 시간", "value", registeredTime)
        );

        Context context = new Context();
        context.setVariable("alertTitle", "패스키 등록 완료");
        context.setVariable("alertMessage", "새로운 패스키가 회원님의 계정에 등록되었습니다.");
        context.setVariable("details", details);

        String html = templateEngine.process("email/security-alert", context);
        sendHtmlEmail(to, "[Authly] 패스키 등록 알림", html);
    }

    @Override
    public void sendPasskeyRemovedAlert(String to, String deviceName, String removedTime) {
        List<Map<String, String>> details = List.of(
                Map.of("key", "기기 이름", "value", deviceName),
                Map.of("key", "삭제 시간", "value", removedTime)
        );

        Context context = new Context();
        context.setVariable("alertTitle", "패스키 삭제됨");
        context.setVariable("alertMessage", deviceName + " 패스키가 삭제되었습니다.");
        context.setVariable("details", details);

        String html = templateEngine.process("email/security-alert", context);
        sendHtmlEmail(to, "[Authly] 패스키 삭제 알림", html);
    }

    @Override
    public void sendPasswordExpiringSoonAlert(String to, int daysLeft, String expireDate) {
        List<Map<String, String>> details = List.of(
                Map.of("key", "만료 예정일", "value", expireDate),
                Map.of("key", "남은 일수", "value", daysLeft + "일")
        );

        Context context = new Context();
        context.setVariable("alertTitle", "비밀번호 만료 예정");
        context.setVariable("alertMessage", daysLeft + "일 후 비밀번호가 만료됩니다. 지금 변경해주세요.");
        context.setVariable("details", details);

        String html = templateEngine.process("email/security-alert", context);
        sendHtmlEmail(to, "[Authly] 비밀번호 만료 " + daysLeft + "일 전 안내", html);
    }

    @Override
    public void sendPasswordExpiredAlert(String to, String expireDate) {
        List<Map<String, String>> details = List.of(
                Map.of("key", "만료일", "value", expireDate)
        );

        Context context = new Context();
        context.setVariable("alertTitle", "비밀번호 만료됨");
        context.setVariable("alertMessage", "비밀번호가 만료되었습니다. 로그인 후 즉시 변경해주세요.");
        context.setVariable("details", details);

        String html = templateEngine.process("email/security-alert", context);
        sendHtmlEmail(to, "[Authly] 비밀번호 만료 안내", html);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(FROM_ADDRESS);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Email sent to: {}, subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to: {}, subject: {}", to, subject, e);
        }
    }
}
