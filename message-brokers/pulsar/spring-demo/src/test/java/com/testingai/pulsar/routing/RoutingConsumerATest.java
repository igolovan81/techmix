package com.testingai.pulsar.routing;

import org.apache.pulsar.client.api.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingConsumerATest {

	@InjectMocks
	private RoutingConsumerA consumer;

	@Mock
	private Message<String> message;

	@Test
	void receive_shouldLogKeyAndValue() {
		when(message.getKey()).thenReturn("info");
		when(message.getValue()).thenReturn("hello");
		assertThatCode(() -> consumer.receive(message)).doesNotThrowAnyException();
	}
}
