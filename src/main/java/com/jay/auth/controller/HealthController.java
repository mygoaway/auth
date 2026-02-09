package com.jay.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Health", description = "Health check API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    @Operation(summary = "Health check", description = "Check if the service is running")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "auth-service");
        response.put("timestamp", LocalDateTime.now());

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("database", checkDatabase());
        components.put("redis", checkRedis());
        response.put("components", components);

        boolean allUp = components.values().stream()
                .allMatch(c -> "UP".equals(((Map<?, ?>) c).get("status")));

        if (!allUp) {
            response.put("status", "DEGRADED");
        }

        return ResponseEntity.ok(response);
    }

    private Map<String, String> checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(3)) {
                return Map.of("status", "UP");
            }
            return Map.of("status", "DOWN", "error", "Connection not valid");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    private Map<String, String> checkRedis() {
        try {
            redisConnectionFactory.getConnection().ping();
            return Map.of("status", "UP");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }
}
