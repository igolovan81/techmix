package com.testingai.redis.pubsub;

import com.testingai.redis.config.StreamKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PubSubPublisher {
    private final RedisTemplate<String, String> redisTemplate;

    public void publish(String message) {
        redisTemplate.convertAndSend(StreamKeys.PUBSUB_CHANNEL, message);
        log.info("[pubsub] published message={}", message);
    }
}
