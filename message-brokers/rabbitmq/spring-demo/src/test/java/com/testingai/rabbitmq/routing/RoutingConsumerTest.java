package com.testingai.rabbitmq.routing;

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
class RoutingConsumerTest {

	@InjectMocks
	private RoutingConsumer consumer;

	@Mock
	private Channel channel;

	@Test
	void receiveAll_shouldAckOnSuccess() throws Exception {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
			consumer.receiveAll("info message", channel, 1L);
			verify(channel).basicAck(1L, false);
			verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
		}
	}

	@Test
	void receiveAll_shouldNackWithRequeueOnSimulatedFailure() throws Exception {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));
			consumer.receiveAll("info message", channel, 1L);
			verify(channel).basicNack(1L, false, true);
			verify(channel, never()).basicAck(anyLong(), anyBoolean());
		}
	}

	@Test
	void receiveError_shouldAckOnSuccess() throws Exception {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
			consumer.receiveError("error message", channel, 2L);
			verify(channel).basicAck(2L, false);
			verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
		}
	}

	@Test
	void receiveError_shouldNackWithRequeueOnSimulatedFailure() throws Exception {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));
			consumer.receiveError("error message", channel, 2L);
			verify(channel).basicNack(2L, false, true);
			verify(channel, never()).basicAck(anyLong(), anyBoolean());
		}
	}
}
