package com.testingai.kafka.workqueue;

import com.testingai.kafka.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class WorkQueueConsumerTest {

    @InjectMocks
    private WorkQueueConsumer consumer;

    @Test
    void worker1_shouldNotThrowOnSuccess() {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            assertThatCode(() -> consumer.worker1("task")).doesNotThrowAnyException();
        }
    }

    @Test
    void worker1_shouldPropagateExceptionOnSimulatedFailure() {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            assertThatThrownBy(() -> consumer.worker1("task"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void worker2_shouldNotThrowOnSuccess() {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            assertThatCode(() -> consumer.worker2("task")).doesNotThrowAnyException();
        }
    }
}
