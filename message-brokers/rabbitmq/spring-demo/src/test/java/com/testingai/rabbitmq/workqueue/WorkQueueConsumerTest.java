package com.testingai.rabbitmq.workqueue;

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
class WorkQueueConsumerTest {

    @InjectMocks
    private WorkQueueConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void worker1_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.worker1("task", channel, 1L);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void worker1_shouldNackWithRequeueOnSimulatedFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.worker1("task", channel, 1L);
            verify(channel).basicNack(1L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }

    @Test
    void worker2_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.worker2("task", channel, 2L);
            verify(channel).basicAck(2L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void worker2_shouldNackWithRequeueOnSimulatedFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.worker2("task", channel, 2L);
            verify(channel).basicNack(2L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }
}
