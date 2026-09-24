package com.gameexpert.chat.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatRateLimitService {

    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= 5 then
                return 0
            end
            local updated = redis.call('INCR', KEYS[1])
            if updated == 1 then
                redis.call('EXPIRE', KEYS[1], 10)
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean allow(Long playerId) {
        String key = "chat:limit:" + playerId;
        Long result = redisTemplate.execute(LIMIT_SCRIPT, List.of(key));
        return result == 1L;
    }
}
