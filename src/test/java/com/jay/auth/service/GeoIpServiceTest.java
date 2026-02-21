package com.jay.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeoIpServiceTest {

    @InjectMocks
    private GeoIpService geoIpService;

    @Mock
    private RestTemplate restTemplate;

    private static final String IP_API_URL = "https://ip-api.com/json/{ip}?fields=status,country,regionName,city,query&lang=ko";

    @Nested
    @DisplayName("정상 IP 위치 조회")
    class GetLocationForNormalIp {

        @Test
        @DisplayName("정상 응답 시 국가 지역 도시가 반환되어야 한다")
        void getLocationWithFullInfo() {
            // given
            String ip = "8.8.8.8";
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("country", "대한민국");
            response.put("regionName", "서울특별시");
            response.put("city", "강남구");

            setRestTemplateField();
            given(restTemplate.getForObject(IP_API_URL, Map.class, ip)).willReturn(response);

            // when
            String result = geoIpService.getLocation(ip);

            // then
            assertThat(result).isEqualTo("대한민국 서울특별시 강남구");
        }

        @Test
        @DisplayName("지역/도시 정보 없이 국가만 반환되어야 한다")
        void getLocationWithCountryOnly() {
            // given
            String ip = "1.1.1.1";
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("country", "미국");
            response.put("regionName", null);
            response.put("city", null);

            setRestTemplateField();
            given(restTemplate.getForObject(IP_API_URL, Map.class, ip)).willReturn(response);

            // when
            String result = geoIpService.getLocation(ip);

            // then
            assertThat(result).isEqualTo("미국");
        }

        @Test
        @DisplayName("API가 실패 상태를 반환하면 null이 반환되어야 한다")
        void getLocationWithFailStatus() {
            // given
            String ip = "8.8.8.8";
            Map<String, Object> response = new HashMap<>();
            response.put("status", "fail");

            setRestTemplateField();
            given(restTemplate.getForObject(IP_API_URL, Map.class, ip)).willReturn(response);

            // when
            String result = geoIpService.getLocation(ip);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("API 응답이 null이면 null이 반환되어야 한다")
        void getLocationWithNullResponse() {
            // given
            String ip = "8.8.8.8";
            setRestTemplateField();
            given(restTemplate.getForObject(IP_API_URL, Map.class, ip)).willReturn(null);

            // when
            String result = geoIpService.getLocation(ip);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("사설 IP 위치 조회")
    class GetLocationForPrivateIp {

        @Test
        @DisplayName("10.x.x.x 대역은 null이 반환되어야 한다")
        void getLocationForPrivateIp10() {
            // when
            String result = geoIpService.getLocation("10.0.0.1");

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("172.16.x.x 대역은 null이 반환되어야 한다")
        void getLocationForPrivateIp172() {
            // when
            String result = geoIpService.getLocation("172.16.0.1");

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("192.168.x.x 대역은 null이 반환되어야 한다")
        void getLocationForPrivateIp192() {
            // when
            String result = geoIpService.getLocation("192.168.1.1");

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("127.0.0.1 (localhost)은 null이 반환되어야 한다")
        void getLocationForLocalhost() {
            // when
            String result = geoIpService.getLocation("127.0.0.1");

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("IPv6 localhost (::1)은 null이 반환되어야 한다")
        void getLocationForIpv6Localhost() {
            // when
            String result = geoIpService.getLocation("::1");

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("IPv6 long form localhost은 null이 반환되어야 한다")
        void getLocationForIpv6LongFormLocalhost() {
            // when
            String result = geoIpService.getLocation("0:0:0:0:0:0:0:1");

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("null/빈 IP 위치 조회")
    class GetLocationForNullOrEmptyIp {

        @Test
        @DisplayName("null IP는 null이 반환되어야 한다")
        void getLocationForNullIp() {
            // when
            String result = geoIpService.getLocation(null);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("API 실패 시 위치 조회")
    class GetLocationOnApiFailure {

        @Test
        @DisplayName("API 호출 중 예외 발생 시 null이 반환되어야 한다")
        void getLocationOnRestClientException() {
            // given
            String ip = "8.8.8.8";
            setRestTemplateField();
            given(restTemplate.getForObject(IP_API_URL, Map.class, ip))
                    .willThrow(new RestClientException("Connection timeout"));

            // when
            String result = geoIpService.getLocation(ip);

            // then
            assertThat(result).isNull();
        }
    }

    /**
     * GeoIpService는 RestTemplate을 내부적으로 new RestTemplate()으로 생성하므로
     * Mock을 주입하기 위해 리플렉션을 사용
     */
    private void setRestTemplateField() {
        try {
            java.lang.reflect.Field field = GeoIpService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(geoIpService, restTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
