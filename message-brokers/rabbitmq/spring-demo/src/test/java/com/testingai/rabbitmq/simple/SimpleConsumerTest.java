package com.testingai.rabbitmq.simple;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleConsumerTest {

    @InjectMocks
    private SimpleConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void receive_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.receive("hello", channel, 1L, false);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void receive_shouldNackWithRequeueOnFirstFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.receive("hello", channel, 1L, false);
            verify(channel).basicNack(1L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }

    @Test
    void receive_shouldNackWithoutRequeueOnRedeliveredFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.receive("hello", channel, 1L, true);
            verify(channel).basicNack(1L, false, false);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }
}
