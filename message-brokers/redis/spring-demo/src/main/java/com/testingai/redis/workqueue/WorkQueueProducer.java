package com.testingai.redis.workqueue;

import com.testingai.redis.config.StreamKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkQueueProducer {

    private final RedisTemplate<String, String> redisTemplate;

    public void send(String message) {
        var id = redisTemplate.opsForStream()
                .add(StreamKeys.WORK, Map.of("message", message));
        log.info("[work-queue] sent id={} message={}", id, message);
    }
}
