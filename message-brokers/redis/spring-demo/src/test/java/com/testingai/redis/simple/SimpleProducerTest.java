package com.testingai.redis.simple;

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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleProducerTest {

	@Mock
	RedisTemplate<String, String> redisTemplate;

	@Mock
	StreamOperations<String, Object, Object> streamOps;

	@InjectMocks
	SimpleProducer producer;

	@Test
	void sendAddsMessageToSimpleStream() {
		doReturn(streamOps).when(redisTemplate).opsForStream();

		producer.send("hello");

		verify(streamOps).add(eq(StreamKeys.SIMPLE), any(java.util.Map.class));
	}
}
