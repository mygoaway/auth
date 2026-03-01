package com.jay.auth.service;

import com.jay.auth.dto.response.TrustedDeviceResponse;
import com.jay.auth.security.TokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.jay.auth.util.DateTimeUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrustedDeviceService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TRUSTED_DEVICE_PREFIX = "trusted:";
    private static final long TRUSTED_DEVICE_TTL_DAYS = 30;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeUtil.ISO_FORMATTER;

    public String generateDeviceId(TokenStore.SessionInfo sessionInfo) {
        String raw = (sessionInfo.browser() != null ? sessionInfo.browser() : "") + "|"
                + (sessionInfo.os() != null ? sessionInfo.os() : "") + "|"
                + (sessionInfo.deviceType() != null ? sessionInfo.deviceType() : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    public void trustDevice(Long userId, TokenStore.SessionInfo sessionInfo) {
        String deviceId = generateDeviceId(sessionInfo);
        String key = buildKey(userId, deviceId);

        Map<String, String> deviceData = new HashMap<>();
        deviceData.put("deviceType", sessionInfo.deviceType() != null ? sessionInfo.deviceType() : "Unknown");
        deviceData.put("browser", sessionInfo.browser() != null ? sessionInfo.browser() : "Unknown");
        deviceData.put("os", sessionInfo.os() != null ? sessionInfo.os() : "Unknown");
        deviceData.put("ipAddress", sessionInfo.ipAddress() != null ? sessionInfo.ipAddress() : "");
        deviceData.put("location", sessionInfo.location() != null ? sessionInfo.location() : "");
        deviceData.put("trustedAt", LocalDateTime.now().format(DATE_FORMATTER));
        deviceData.put("lastUsedAt", LocalDateTime.now().format(DATE_FORMATTER));

        redisTemplate.opsForHash().putAll(key, deviceData);
        redisTemplate.expire(key, TRUSTED_DEVICE_TTL_DAYS, TimeUnit.DAYS);

        log.info("Device trusted: userId={}, deviceId={}", userId, deviceId);
    }

    public boolean isDeviceTrusted(Long userId, TokenStore.SessionInfo sessionInfo) {
        String deviceId = generateDeviceId(sessionInfo);
        String key = buildKey(userId, deviceId);
        boolean exists = Boolean.TRUE.equals(redisTemplate.hasKey(key));

        if (exists) {
            redisTemplate.opsForHash().put(key, "lastUsedAt", LocalDateTime.now().format(DATE_FORMATTER));
        }

        return exists;
    }

    public List<TrustedDeviceResponse> getTrustedDevices(Long userId) {
        String pattern = TRUSTED_DEVICE_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        List<TrustedDeviceResponse> devices = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                Map<Object, Object> rawData = redisTemplate.opsForHash().entries(key);
                if (!rawData.isEmpty()) {
                    String deviceId = key.substring(key.lastIndexOf(":") + 1);
                    devices.add(TrustedDeviceResponse.builder()
                            .deviceId(deviceId)
                            .deviceType(getStr(rawData, "deviceType"))
                            .browser(getStr(rawData, "browser"))
                            .os(getStr(rawData, "os"))
                            .ipAddress(getStr(rawData, "ipAddress"))
                            .location(getStr(rawData, "location"))
                            .trustedAt(getStr(rawData, "trustedAt"))
                            .lastUsedAt(getStr(rawData, "lastUsedAt"))
                            .build());
                }
            }
        }

        devices.sort((a, b) -> {
            if (b.getLastUsedAt() == null) return -1;
            if (a.getLastUsedAt() == null) return 1;
            return b.getLastUsedAt().compareTo(a.getLastUsedAt());
        });

        return devices;
    }

    public boolean isTrustedDevice(Long userId, String deviceId) {
        String key = buildKey(userId, deviceId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void removeTrustedDevice(Long userId, String deviceId) {
        String key = buildKey(userId, deviceId);
        redisTemplate.delete(key);
        log.info("Trusted device removed: userId={}, deviceId={}", userId, deviceId);
    }

    public void removeAllTrustedDevices(Long userId) {
        String pattern = TRUSTED_DEVICE_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("All trusted devices removed: userId={}, count={}", userId, keys.size());
        }
    }

    private String buildKey(Long userId, String deviceId) {
        return TRUSTED_DEVICE_PREFIX + userId + ":" + deviceId;
    }

    private String getStr(Map<Object, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : "";
    }
}
