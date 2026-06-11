package com.testingai.redis.trimming;

import com.testingai.redis.config.StreamKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrimmingProducer {
    private final RedisTemplate<String, String> redisTemplate;

    public void send(String message) {
        var id = redisTemplate.opsForStream()
                .add(StreamKeys.TRIMMED, Map.of("message", message));
        redisTemplate.opsForStream().trim(StreamKeys.TRIMMED, StreamKeys.TRIM_MAX_LEN);
        log.info("[trimming] sent id={} message={}", id, message);
    }
}
