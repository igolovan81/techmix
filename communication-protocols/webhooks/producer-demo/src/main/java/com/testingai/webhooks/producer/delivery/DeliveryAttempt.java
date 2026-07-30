package com.testingai.webhooks.producer.delivery;

import java.time.Instant;

public class DeliveryAttempt {

	private final String deliveryId;
	private final String subscriptionId;
	private final String eventType;
	private final String body;
	private final String callbackUrl;
	private final String secret;

	private volatile DeliveryStatus status = DeliveryStatus.PENDING;
	private volatile int attemptCount = 0;
	private volatile Instant nextRetryAt;

	public DeliveryAttempt(String deliveryId, String subscriptionId, String eventType, String body, String callbackUrl,
			String secret) {
		this.deliveryId = deliveryId;
		this.subscriptionId = subscriptionId;
		this.eventType = eventType;
		this.body = body;
		this.callbackUrl = callbackUrl;
		this.secret = secret;
	}

	public void incrementAttemptCount() {
		attemptCount++;
	}

	public void markSucceeded() {
		status = DeliveryStatus.SUCCEEDED;
		nextRetryAt = null;
	}

	public void markRetrying(Instant nextRetryAt) {
		status = DeliveryStatus.RETRYING;
		this.nextRetryAt = nextRetryAt;
	}

	public void markDeadLettered() {
		status = DeliveryStatus.DEAD_LETTERED;
		nextRetryAt = null;
	}

	public String deliveryId() {
		return deliveryId;
	}

	public String subscriptionId() {
		return subscriptionId;
	}

	public String eventType() {
		return eventType;
	}

	public String body() {
		return body;
	}

	public String callbackUrl() {
		return callbackUrl;
	}

	public String secret() {
		return secret;
	}

	public DeliveryStatus status() {
		return status;
	}

	public int attemptCount() {
		return attemptCount;
	}

	public Instant nextRetryAt() {
		return nextRetryAt;
	}
}
