package com.testingai.redis.workqueue;

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
class WorkQueueProducerTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock StreamOperations<String, Object, Object> streamOps;
    @InjectMocks WorkQueueProducer producer;

    @Test
    void sendAddsMessageToWorkStream() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);

        producer.send("task-1");

        verify(streamOps).add(eq(StreamKeys.WORK), any(java.util.Map.class));
    }
}
