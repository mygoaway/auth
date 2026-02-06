package com.jay.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service to manage OAuth2 account linking state.
 * Stores link mode information in Redis during the OAuth2 flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2LinkStateService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LINK_STATE_PREFIX = "oauth2:link:";
    private static final Duration LINK_STATE_TTL = Duration.ofMinutes(10);

    /**
     * Store link mode state for an OAuth2 authorization request
     * @param state The OAuth2 state parameter
     * @param userId The user ID to link the social account to
     */
    public void saveLinkState(String state, Long userId) {
        String key = LINK_STATE_PREFIX + state;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), LINK_STATE_TTL);
        log.debug("Saved link state: state={}, userId={}", state, userId);
    }

    /**
     * Get the user ID for link mode from the OAuth2 state
     * @param state The OAuth2 state parameter
     * @return The user ID if in link mode, null otherwise
     */
    public Long getLinkUserId(String state) {
        if (state == null) {
            return null;
        }
        String key = LINK_STATE_PREFIX + state;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId != null) {
            log.debug("Found link state: state={}, userId={}", state, userId);
            return Long.parseLong(userId);
        }
        return null;
    }

    /**
     * Remove link state after OAuth2 flow completes
     * @param state The OAuth2 state parameter
     */
    public void removeLinkState(String state) {
        if (state != null) {
            String key = LINK_STATE_PREFIX + state;
            redisTemplate.delete(key);
            log.debug("Removed link state: state={}", state);
        }
    }
}
