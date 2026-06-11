package com.testingai.redis.trimming;

import com.testingai.redis.config.StreamKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrimmingConsumerTest {
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock StreamOperations<String, Object, Object> streamOps;
    @InjectMocks TrimmingConsumer consumer;

    @Test
    void onMessageAcknowledgesAndLogsLength() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(streamOps.size(StreamKeys.TRIMMED)).thenReturn(42L);
        var record = MapRecord.create(StreamKeys.TRIMMED, Map.of("message", "hello"))
                .withId(RecordId.of("1-0"));

        consumer.onMessage(record);

        verify(streamOps).acknowledge(eq(StreamKeys.TRIMMED), eq("trimmed-group"), any(RecordId.class));
        verify(streamOps).size(StreamKeys.TRIMMED);
    }
}
