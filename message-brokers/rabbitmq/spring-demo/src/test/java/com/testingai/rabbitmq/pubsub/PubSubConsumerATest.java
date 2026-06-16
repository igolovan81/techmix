package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubSubConsumerATest {

	@InjectMocks
	private PubSubConsumerA consumer;

	@Mock
	private Channel channel;

	@Test
	void receive_shouldAckOnSuccess() throws Exception {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
			consumer.receive("broadcast", channel, 1L, null);
			verify(channel).basicAck(1L, false);
			verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
		}
	}

	@Test
	void receive_shouldNackToDlxOnFirstFailure() throws Exception {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));
			consumer.receive("broadcast", channel, 1L, null);
			verify(channel).basicNack(1L, false, false);
			verify(channel, never()).basicAck(anyLong(), anyBoolean());
		}
	}

	@Test
	void receive_shouldDiscardAfterMaxRetries() throws Exception {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));
			List<Map<String, Object>> xDeath = List.of(Map.of("count", (long) PubSubConfig.MAX_RETRIES));
			consumer.receive("broadcast", channel, 1L, xDeath);
			verify(channel).basicAck(1L, false);
			verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
		}
	}
}
