package com.testingai.redis.fanout;

import com.testingai.redis.config.StreamKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FanoutProducer {
	private final RedisTemplate<String, String> redisTemplate;

	public void send(String message) {
		var id = redisTemplate.opsForStream().add(StreamKeys.FANOUT, Map.of("message", message));
		log.info("[fanout] sent id={} message={}", id, message);
	}
}
