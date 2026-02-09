package com.jay.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CoolSMS API를 이용한 SMS 발송 구현체
 * app.sms.provider=coolsms 설정 시 활성화
 *
 * CoolSMS 가입 후 API Key/Secret 발급 필요:
 * https://coolsms.co.kr
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "coolsms")
public class CoolSmsSender implements SmsSender {

    private static final String API_URL = "https://api.coolsms.co.kr/messages/v4/send";

    @Value("${app.sms.coolsms.api-key}")
    private String apiKey;

    @Value("${app.sms.coolsms.api-secret}")
    private String apiSecret;

    @Value("${app.sms.coolsms.sender}")
    private String senderPhone;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendVerificationCode(String phone, String code) {
        String message = String.format("[Authly] 인증 코드: %s (3분 내 입력해주세요)", code);

        try {
            String cleanPhone = phone.replaceAll("-", "");

            String salt = UUID.randomUUID().toString().replace("-", "");
            String date = java.time.Instant.now().toString();
            String signature = generateSignature(date, salt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", String.format(
                    "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s",
                    apiKey, date, salt, signature
            ));

            Map<String, Object> body = new HashMap<>();
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("to", cleanPhone);
            messageMap.put("from", senderPhone);
            messageMap.put("text", message);
            messageMap.put("type", "SMS");
            body.put("message", messageMap);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("SMS sent successfully to: {}", cleanPhone);
            } else {
                log.error("SMS send failed. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("SMS 발송에 실패했습니다.");
            }
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phone, e.getMessage());
            throw new RuntimeException("SMS 발송에 실패했습니다.", e);
        }
    }

    private String generateSignature(String date, String salt) {
        try {
            String data = date + salt;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC signature generation failed", e);
        }
    }
}
