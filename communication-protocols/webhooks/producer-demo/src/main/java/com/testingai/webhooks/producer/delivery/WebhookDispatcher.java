package com.testingai.webhooks.producer.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.webhooks.producer.event.OrderEvent;
import com.testingai.webhooks.producer.security.HmacSigner;
import com.testingai.webhooks.producer.subscription.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebhookDispatcher {

	private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

	private final RestClient restClient;
	private final TaskScheduler taskScheduler;
	private final HmacSigner hmacSigner;
	private final ObjectMapper objectMapper;
	private final RetryBackoffSchedule retryBackoffSchedule;
	private final Map<String, DeliveryAttempt> deliveries = new ConcurrentHashMap<>();

	public WebhookDispatcher(RestClient restClient, TaskScheduler taskScheduler, HmacSigner hmacSigner,
			ObjectMapper objectMapper, RetryBackoffSchedule retryBackoffSchedule) {
		this.restClient = restClient;
		this.taskScheduler = taskScheduler;
		this.hmacSigner = hmacSigner;
		this.objectMapper = objectMapper;
		this.retryBackoffSchedule = retryBackoffSchedule;
	}

	public String dispatch(Subscription subscription, OrderEvent event) {
		String deliveryId = UUID.randomUUID().toString();
		String body = writeJson(event);
		DeliveryAttempt attempt = new DeliveryAttempt(deliveryId, subscription.id(), event.eventType(), body,
				subscription.callbackUrl(), subscription.secret());
		deliveries.put(deliveryId, attempt);
		attemptDelivery(attempt);
		return deliveryId;
	}

	public Collection<DeliveryAttempt> deliveries() {
		return deliveries.values();
	}

	public Collection<DeliveryAttempt> deadLetters() {
		return deliveries.values().stream().filter(attempt -> attempt.status() == DeliveryStatus.DEAD_LETTERED)
				.toList();
	}

	private void attemptDelivery(DeliveryAttempt attempt) {
		attempt.incrementAttemptCount();
		try {
			String signature = "sha256=" + hmacSigner.sign(attempt.secret(), attempt.body());
			restClient.post().uri(attempt.callbackUrl()).header("X-Webhook-Id", attempt.deliveryId())
					.header("X-Webhook-Event", attempt.eventType()).header("X-Webhook-Signature", signature)
					.contentType(MediaType.APPLICATION_JSON).body(attempt.body()).retrieve().toBodilessEntity();
			attempt.markSucceeded();
			log.info("delivery {} succeeded on attempt {}", attempt.deliveryId(), attempt.attemptCount());
		} catch (RestClientException e) {
			handleFailure(attempt);
		}
	}

	private void handleFailure(DeliveryAttempt attempt) {
		if (attempt.attemptCount() >= retryBackoffSchedule.maxAttempts()) {
			attempt.markDeadLettered();
			log.warn("delivery {} dead-lettered after {} attempts", attempt.deliveryId(), attempt.attemptCount());
			return;
		}
		Instant nextRetryAt = Instant.now().plus(retryBackoffSchedule.delayForAttempt(attempt.attemptCount()));
		attempt.markRetrying(nextRetryAt);
		log.info("delivery {} failed on attempt {}, retrying at {}", attempt.deliveryId(), attempt.attemptCount(),
				nextRetryAt);
		taskScheduler.schedule(() -> attemptDelivery(attempt), nextRetryAt);
	}

	private String writeJson(OrderEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Unable to serialize order event", e);
		}
	}
}
