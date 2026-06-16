package com.testingai.servicebus.routing;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.testingai.servicebus.config.EntityNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingPublisherTest {

	@Mock
	private ServiceBusClientBuilder clientBuilder;
	@Mock
	private ServiceBusClientBuilder.ServiceBusSenderClientBuilder senderClientBuilder;
	@Mock
	private ServiceBusSenderClient senderClient;

	private RoutingPublisher publisher;

	@BeforeEach
	void setUp() {
		when(clientBuilder.sender()).thenReturn(senderClientBuilder);
		when(senderClientBuilder.topicName(EntityNames.ROUTING_TOPIC)).thenReturn(senderClientBuilder);
		when(senderClientBuilder.buildClient()).thenReturn(senderClient);
		publisher = new RoutingPublisher(clientBuilder);
	}

	@Test
	void publish_shouldSendMessageWithLevelProperty() {
		publisher.publish("error", "boom");

		ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
		verify(senderClient).sendMessage(captor.capture());
		assertThat(captor.getValue().getBody().toString()).isEqualTo("boom");
		assertThat(captor.getValue().getApplicationProperties().get(EntityNames.ROUTING_KEY)).isEqualTo("error");
	}

	@Test
	void publish_shouldSetLevelPropertyFromKey() {
		publisher.publish("info", "hello");

		ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
		verify(senderClient).sendMessage(captor.capture());
		assertThat(captor.getValue().getApplicationProperties().get(EntityNames.ROUTING_KEY)).isEqualTo("info");
	}
}
