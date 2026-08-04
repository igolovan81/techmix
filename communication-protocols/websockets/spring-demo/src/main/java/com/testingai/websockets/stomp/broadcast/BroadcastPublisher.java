package com.testingai.websockets.stomp.broadcast;

import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class BroadcastPublisher implements OrderEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	public BroadcastPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void publish(OrderEvent event) {
		messagingTemplate.convertAndSend("/topic/orders", event);
	}
}
