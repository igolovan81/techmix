package com.testingai.webhooks.producer.delivery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class DeliveryController {

	public record DeliveryView(String deliveryId, String subscriptionId, String eventType, DeliveryStatus status,
			int attemptCount, Instant nextRetryAt) {
	}

	private final WebhookDispatcher webhookDispatcher;

	public DeliveryController(WebhookDispatcher webhookDispatcher) {
		this.webhookDispatcher = webhookDispatcher;
	}

	@GetMapping("/deliveries")
	public List<DeliveryView> deliveries() {
		return webhookDispatcher.deliveries().stream().map(this::toView).toList();
	}

	@GetMapping("/deliveries/dead-letter")
	public List<DeliveryView> deadLetters() {
		return webhookDispatcher.deadLetters().stream().map(this::toView).toList();
	}

	private DeliveryView toView(DeliveryAttempt attempt) {
		return new DeliveryView(attempt.deliveryId(), attempt.subscriptionId(), attempt.eventType(), attempt.status(),
				attempt.attemptCount(), attempt.nextRetryAt());
	}
}
