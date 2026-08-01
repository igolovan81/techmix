package com.testingai.websockets.stomp.topic;

import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderTopicPublisherTest {

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
	private final OrderTopicPublisher publisher = new OrderTopicPublisher(messagingTemplate);

	@Test
	void publish_sendsEventToPerOrderTopic() {
		OrderEvent event = new OrderEvent("order-1", OrderStatus.PAID, Instant.now());

		publisher.publish(event);

		verify(messagingTemplate).convertAndSend(eq("/topic/orders/order-1"), eq(event));
	}
}
