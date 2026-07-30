package com.testingai.webhooks.consumer.receiver;

import java.time.Instant;

public record ReceivedEvent(String deliveryId, String eventType, String orderId, Instant receivedAt,
		boolean duplicate) {
}
