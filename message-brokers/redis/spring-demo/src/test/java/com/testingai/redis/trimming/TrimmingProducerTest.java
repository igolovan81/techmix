package com.testingai.redis.trimming;

import com.testingai.redis.config.StreamKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrimmingProducerTest {
	@Mock
	RedisTemplate<String, String> redisTemplate;
	@Mock
	StreamOperations<String, Object, Object> streamOps;
	@InjectMocks
	TrimmingProducer producer;

	@Test
	void sendAddsAndTrimsStream() {
		when(redisTemplate.opsForStream()).thenReturn(streamOps);
		producer.send("hello");
		verify(streamOps).add(any(), any(java.util.Map.class));
		verify(streamOps).trim(StreamKeys.TRIMMED, StreamKeys.TRIM_MAX_LEN);
	}
}
