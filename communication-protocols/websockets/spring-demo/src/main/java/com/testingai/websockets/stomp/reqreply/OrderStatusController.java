package com.testingai.websockets.stomp.reqreply;

import com.testingai.websockets.domain.Order;
import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderTrackingService;
import com.testingai.websockets.util.FailureSimulator;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class OrderStatusController {

	private final OrderTrackingService orderTrackingService;
	private final SimpMessagingTemplate messagingTemplate;

	public OrderStatusController(OrderTrackingService orderTrackingService, SimpMessagingTemplate messagingTemplate) {
		this.orderTrackingService = orderTrackingService;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/orders/{id}/status-request")
	public void statusRequest(@DestinationVariable String id, SimpMessageHeaderAccessor headerAccessor) {
		FailureSimulator.maybeThrow("status-request");
		Order order = orderTrackingService.get(id);
		OrderEvent event = new OrderEvent(order.id(), order.status(), order.updatedAt());
		String sessionId = headerAccessor.getSessionId();
		messagingTemplate.convertAndSendToUser(sessionId, "/queue/orders/" + id + "/status", event,
				sessionHeaders(sessionId));
	}

	private MessageHeaders sessionHeaders(String sessionId) {
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		accessor.setSessionId(sessionId);
		accessor.setLeaveMutable(true);
		return accessor.getMessageHeaders();
	}
}
