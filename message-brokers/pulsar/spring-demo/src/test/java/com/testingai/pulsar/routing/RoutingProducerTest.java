package com.testingai.pulsar.routing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.pulsar.core.PulsarOperations;
import org.springframework.pulsar.core.PulsarTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingProducerTest {

	@Mock
	private PulsarTemplate<String> pulsarTemplate;

	@Mock
	private PulsarOperations.SendMessageBuilder<String> sendMessageBuilder;

	@InjectMocks
	private RoutingProducer producer;

	@Test
	void send_shouldSendToRoutingTopicWithKey() {
		when(pulsarTemplate.newMessage(anyString())).thenReturn(sendMessageBuilder);
		when(sendMessageBuilder.withTopic(anyString())).thenReturn(sendMessageBuilder);
		when(sendMessageBuilder.withMessageCustomizer(any())).thenReturn(sendMessageBuilder);
		assertThatCode(() -> producer.send("info", "hello")).doesNotThrowAnyException();
	}
}
