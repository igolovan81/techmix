package com.testingai.redis.pending;

import com.testingai.redis.config.StreamKeys;
import com.testingai.redis.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingConsumerTest {
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock StreamOperations<String, Object, Object> streamOps;
    @InjectMocks PendingConsumer consumer;

    @Test
    void onMessageAcknowledgesOnSuccess() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        var record = MapRecord.create(StreamKeys.PENDING, Map.of("message", "hello"))
                .withId(RecordId.of("1-0"));

        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(any())).thenAnswer(inv -> null);
            consumer.onMessage(record);
        }

        verify(streamOps).acknowledge(eq(StreamKeys.PENDING), eq("pending-group"), any(RecordId.class));
    }

    @Test
    void onMessageSkipsAckOnFailure() {
        var record = MapRecord.create(StreamKeys.PENDING, Map.of("message", "hello"))
                .withId(RecordId.of("1-0"));

        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(any()))
                    .thenThrow(new RuntimeException("simulated"));
            consumer.onMessage(record);
        }

        verify(redisTemplate, never()).opsForStream();
    }
}
