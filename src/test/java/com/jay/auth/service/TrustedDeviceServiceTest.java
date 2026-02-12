package com.jay.auth.service;

import com.jay.auth.dto.response.TrustedDeviceResponse;
import com.jay.auth.security.TokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrustedDeviceServiceTest {

    @InjectMocks
    private TrustedDeviceService trustedDeviceService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Nested
    @DisplayName("기기 ID 생성")
    class GenerateDeviceId {

        @Test
        @DisplayName("동일한 세션 정보로 동일한 해시가 생성되어야 한다")
        void generateConsistentDeviceId() {
            // given
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows 10", "192.168.1.1", "Seoul");

            // when
            String deviceId1 = trustedDeviceService.generateDeviceId(sessionInfo);
            String deviceId2 = trustedDeviceService.generateDeviceId(sessionInfo);

            // then
            assertThat(deviceId1).isNotNull();
            assertThat(deviceId1).hasSize(16); // 8 bytes = 16 hex chars
            assertThat(deviceId1).isEqualTo(deviceId2);
        }

        @Test
        @DisplayName("다른 세션 정보로 다른 해시가 생성되어야 한다")
        void generateDifferentDeviceIdForDifferentSession() {
            // given
            TokenStore.SessionInfo sessionInfo1 = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows 10", "192.168.1.1", "Seoul");
            TokenStore.SessionInfo sessionInfo2 = new TokenStore.SessionInfo(
                    "Mobile", "Safari", "iOS 17", "192.168.1.2", "Busan");

            // when
            String deviceId1 = trustedDeviceService.generateDeviceId(sessionInfo1);
            String deviceId2 = trustedDeviceService.generateDeviceId(sessionInfo2);

            // then
            assertThat(deviceId1).isNotEqualTo(deviceId2);
        }

        @Test
        @DisplayName("null 필드가 있어도 정상적으로 해시가 생성되어야 한다")
        void generateDeviceIdWithNullFields() {
            // given
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    null, null, null, null, null);

            // when
            String deviceId = trustedDeviceService.generateDeviceId(sessionInfo);

            // then
            assertThat(deviceId).isNotNull();
            assertThat(deviceId).hasSize(16);
        }
    }

    @Nested
    @DisplayName("기기 등록")
    class TrustDevice {

        @Test
        @DisplayName("기기가 Redis에 정상적으로 등록되어야 한다")
        void trustDeviceSuccessfully() {
            // given
            Long userId = 1L;
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows 10", "192.168.1.1", "Seoul");

            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            trustedDeviceService.trustDevice(userId, sessionInfo);

            // then
            verify(hashOperations).putAll(anyString(), anyMap());
            verify(redisTemplate).expire(anyString(), eq(30L), eq(TimeUnit.DAYS));
        }
    }

    @Nested
    @DisplayName("신뢰 기기 확인")
    class IsDeviceTrusted {

        @Test
        @DisplayName("등록된 기기는 신뢰 기기로 판단되어야 한다")
        void trustedDeviceShouldReturnTrue() {
            // given
            Long userId = 1L;
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows 10", "192.168.1.1", "Seoul");
            String deviceId = trustedDeviceService.generateDeviceId(sessionInfo);
            String key = "trusted:" + userId + ":" + deviceId;

            given(redisTemplate.hasKey(key)).willReturn(true);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            boolean result = trustedDeviceService.isDeviceTrusted(userId, sessionInfo);

            // then
            assertThat(result).isTrue();
            verify(hashOperations).put(eq(key), eq("lastUsedAt"), anyString());
        }

        @Test
        @DisplayName("미등록 기기는 신뢰하지 않는 기기로 판단되어야 한다")
        void untrustedDeviceShouldReturnFalse() {
            // given
            Long userId = 1L;
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Firefox", "Linux", "10.0.0.1", "Busan");
            String deviceId = trustedDeviceService.generateDeviceId(sessionInfo);
            String key = "trusted:" + userId + ":" + deviceId;

            given(redisTemplate.hasKey(key)).willReturn(false);

            // when
            boolean result = trustedDeviceService.isDeviceTrusted(userId, sessionInfo);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("신뢰 기기 목록 조회")
    class GetTrustedDevices {

        @Test
        @DisplayName("등록된 모든 신뢰 기기 목록을 조회해야 한다")
        void getTrustedDevicesList() {
            // given
            Long userId = 1L;
            String pattern = "trusted:" + userId + ":*";
            String key1 = "trusted:" + userId + ":abc123";
            String key2 = "trusted:" + userId + ":def456";

            Set<String> keys = new LinkedHashSet<>(Arrays.asList(key1, key2));
            given(redisTemplate.keys(pattern)).willReturn(keys);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            Map<Object, Object> deviceData1 = new HashMap<>();
            deviceData1.put("deviceType", "Desktop");
            deviceData1.put("browser", "Chrome");
            deviceData1.put("os", "Windows 10");
            deviceData1.put("ipAddress", "192.168.1.1");
            deviceData1.put("location", "Seoul");
            deviceData1.put("trustedAt", "2025-01-01T00:00:00");
            deviceData1.put("lastUsedAt", "2025-01-02T00:00:00");

            Map<Object, Object> deviceData2 = new HashMap<>();
            deviceData2.put("deviceType", "Mobile");
            deviceData2.put("browser", "Safari");
            deviceData2.put("os", "iOS 17");
            deviceData2.put("ipAddress", "10.0.0.1");
            deviceData2.put("location", "Busan");
            deviceData2.put("trustedAt", "2025-01-01T00:00:00");
            deviceData2.put("lastUsedAt", "2025-01-03T00:00:00");

            given(hashOperations.entries(key1)).willReturn(deviceData1);
            given(hashOperations.entries(key2)).willReturn(deviceData2);

            // when
            List<TrustedDeviceResponse> devices = trustedDeviceService.getTrustedDevices(userId);

            // then
            assertThat(devices).hasSize(2);
            // Sorted by lastUsedAt descending
            assertThat(devices.get(0).getDeviceType()).isEqualTo("Mobile");
            assertThat(devices.get(1).getDeviceType()).isEqualTo("Desktop");
        }

        @Test
        @DisplayName("키가 없으면 빈 목록을 반환해야 한다")
        void returnEmptyListWhenNoKeys() {
            // given
            Long userId = 1L;
            String pattern = "trusted:" + userId + ":*";
            given(redisTemplate.keys(pattern)).willReturn(null);

            // when
            List<TrustedDeviceResponse> devices = trustedDeviceService.getTrustedDevices(userId);

            // then
            assertThat(devices).isEmpty();
        }
    }

    @Nested
    @DisplayName("신뢰 기기 단일 삭제")
    class RemoveTrustedDevice {

        @Test
        @DisplayName("특정 기기가 Redis에서 삭제되어야 한다")
        void removeSingleDevice() {
            // given
            Long userId = 1L;
            String deviceId = "abc123";
            String key = "trusted:" + userId + ":" + deviceId;

            // when
            trustedDeviceService.removeTrustedDevice(userId, deviceId);

            // then
            verify(redisTemplate).delete(key);
        }
    }

    @Nested
    @DisplayName("신뢰 기기 전체 삭제")
    class RemoveAllTrustedDevices {

        @Test
        @DisplayName("사용자의 모든 신뢰 기기가 삭제되어야 한다")
        void removeAllDevices() {
            // given
            Long userId = 1L;
            String pattern = "trusted:" + userId + ":*";
            Set<String> keys = new HashSet<>(Arrays.asList(
                    "trusted:" + userId + ":abc123",
                    "trusted:" + userId + ":def456"));
            given(redisTemplate.keys(pattern)).willReturn(keys);

            // when
            trustedDeviceService.removeAllTrustedDevices(userId);

            // then
            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("삭제할 기기가 없으면 delete가 호출되지 않아야 한다")
        void doNothingWhenNoDevices() {
            // given
            Long userId = 1L;
            String pattern = "trusted:" + userId + ":*";
            given(redisTemplate.keys(pattern)).willReturn(Collections.emptySet());

            // when
            trustedDeviceService.removeAllTrustedDevices(userId);

            // then
            verify(redisTemplate).keys(pattern);
        }
    }
}
