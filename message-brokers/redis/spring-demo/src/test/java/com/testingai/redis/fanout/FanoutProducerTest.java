package com.testingai.redis.fanout;

import com.testingai.redis.config.StreamKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FanoutProducerTest {
	@Mock
	RedisTemplate<String, String> redisTemplate;
	@Mock
	StreamOperations<String, Object, Object> streamOps;
	@InjectMocks
	FanoutProducer producer;

	@Test
	void sendAddsMessageToFanoutStream() {
		when(redisTemplate.opsForStream()).thenReturn(streamOps);
		producer.send("broadcast");
		verify(streamOps).add(eq(StreamKeys.FANOUT), any(java.util.Map.class));
	}
}
