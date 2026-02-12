package com.jay.auth.controller;

import com.jay.auth.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = HealthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        com.jay.auth.config.RateLimitFilter.class,
                        com.jay.auth.config.RequestLoggingFilter.class,
                        com.jay.auth.config.SecurityHeadersFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Nested
    @DisplayName("GET /api/v1/health")
    class HealthCheck {

        @Test
        @DisplayName("모든 컴포넌트 정상 시 UP 상태")
        void healthAllUp() throws Exception {
            // given
            Connection mockConnection = org.mockito.Mockito.mock(Connection.class);
            given(mockConnection.isValid(anyInt())).willReturn(true);
            given(dataSource.getConnection()).willReturn(mockConnection);

            RedisConnection mockRedisConnection = org.mockito.Mockito.mock(RedisConnection.class);
            given(mockRedisConnection.ping()).willReturn("PONG");
            given(redisConnectionFactory.getConnection()).willReturn(mockRedisConnection);

            // when & then
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.service").value("auth-service"))
                    .andExpect(jsonPath("$.components.database.status").value("UP"))
                    .andExpect(jsonPath("$.components.redis.status").value("UP"));
        }

        @Test
        @DisplayName("데이터베이스 장애 시 DEGRADED 상태")
        void healthDatabaseDown() throws Exception {
            // given
            given(dataSource.getConnection()).willThrow(new SQLException("Connection refused"));

            RedisConnection mockRedisConnection = org.mockito.Mockito.mock(RedisConnection.class);
            given(mockRedisConnection.ping()).willReturn("PONG");
            given(redisConnectionFactory.getConnection()).willReturn(mockRedisConnection);

            // when & then
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DEGRADED"))
                    .andExpect(jsonPath("$.components.database.status").value("DOWN"))
                    .andExpect(jsonPath("$.components.database.error").value("Connection refused"))
                    .andExpect(jsonPath("$.components.redis.status").value("UP"));
        }

        @Test
        @DisplayName("Redis 장애 시 DEGRADED 상태")
        void healthRedisDown() throws Exception {
            // given
            Connection mockConnection = org.mockito.Mockito.mock(Connection.class);
            given(mockConnection.isValid(anyInt())).willReturn(true);
            given(dataSource.getConnection()).willReturn(mockConnection);

            given(redisConnectionFactory.getConnection())
                    .willThrow(new RuntimeException("Redis connection failed"));

            // when & then
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DEGRADED"))
                    .andExpect(jsonPath("$.components.database.status").value("UP"))
                    .andExpect(jsonPath("$.components.redis.status").value("DOWN"))
                    .andExpect(jsonPath("$.components.redis.error").value("Redis connection failed"));
        }
    }
}
