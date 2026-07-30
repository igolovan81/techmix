package com.testingai.webhooks.producer.event;

import com.testingai.webhooks.producer.delivery.WebhookDispatcher;
import com.testingai.webhooks.producer.subscription.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class OrderEventController {

	private final SubscriptionService subscriptionService;
	private final WebhookDispatcher webhookDispatcher;

	public OrderEventController(SubscriptionService subscriptionService, WebhookDispatcher webhookDispatcher) {
		this.subscriptionService = subscriptionService;
		this.webhookDispatcher = webhookDispatcher;
	}

	@PostMapping("/orders/{orderId}/events/{eventType}")
	public ResponseEntity<List<String>> triggerEvent(@PathVariable String orderId, @PathVariable String eventType,
			@RequestBody(required = false) Map<String, Object> data) {
		String fullEventType = "order." + eventType;
		OrderEvent event = new OrderEvent(fullEventType, orderId, Instant.now(), data == null ? Map.of() : data);
		List<String> deliveryIds = subscriptionService.findByEventType(fullEventType).stream()
				.map(subscription -> webhookDispatcher.dispatch(subscription, event)).toList();
		return ResponseEntity.accepted().body(deliveryIds);
	}
}
