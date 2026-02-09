package com.jay.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class GeoIpService {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String IP_API_URL = "http://ip-api.com/json/{ip}?fields=status,country,regionName,city,query&lang=ko";

    /**
     * IP 주소로 위치 정보 조회
     * 결과를 캐시하여 동일 IP에 대한 반복 API 호출 방지
     */
    @Cacheable(value = "geoip", key = "#ipAddress", unless = "#result == null")
    public String getLocation(String ipAddress) {
        if (isPrivateIp(ipAddress)) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    IP_API_URL, Map.class, ipAddress);

            if (response != null && "success".equals(response.get("status"))) {
                String country = (String) response.get("country");
                String region = (String) response.get("regionName");
                String city = (String) response.get("city");

                if (city != null && region != null) {
                    return country + " " + region + " " + city;
                } else if (country != null) {
                    return country;
                }
            }
        } catch (Exception e) {
            log.warn("GeoIP lookup failed for IP {}: {}", ipAddress, e.getMessage());
        }

        return null;
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null) return true;
        return ip.startsWith("10.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.")
                || ip.startsWith("172.19.") || ip.startsWith("172.20.") || ip.startsWith("172.21.")
                || ip.startsWith("172.22.") || ip.startsWith("172.23.") || ip.startsWith("172.24.")
                || ip.startsWith("172.25.") || ip.startsWith("172.26.") || ip.startsWith("172.27.")
                || ip.startsWith("172.28.") || ip.startsWith("172.29.") || ip.startsWith("172.30.")
                || ip.startsWith("172.31.")
                || ip.startsWith("192.168.")
                || ip.equals("127.0.0.1")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.equals("::1");
    }
}
