package com.testingai.websockets.stomp.topic;

import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderTopicPublisher implements OrderEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	public OrderTopicPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void publish(OrderEvent event) {
		messagingTemplate.convertAndSend("/topic/orders/" + event.orderId(), event);
	}
}
