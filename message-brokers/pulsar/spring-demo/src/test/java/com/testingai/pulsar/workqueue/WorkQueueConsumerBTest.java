package com.testingai.pulsar.workqueue;

import com.testingai.pulsar.util.FailureSimulator;
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
class WorkQueueConsumerBTest {

	@InjectMocks
	private WorkQueueConsumerB consumer;

	@Test
	void receive_shouldNotThrowOnSuccess() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
			assertThatCode(() -> consumer.receive("task")).doesNotThrowAnyException();
		}
	}

	@Test
	void receive_shouldPropagateExceptionOnSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));
			assertThatThrownBy(() -> consumer.receive("task")).isInstanceOf(RuntimeException.class);
		}
	}
}
