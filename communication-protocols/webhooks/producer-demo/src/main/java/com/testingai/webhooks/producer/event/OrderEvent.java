package com.testingai.webhooks.producer.event;

import java.time.Instant;
import java.util.Map;

public record OrderEvent(String eventType, String orderId, Instant occurredAt, Map<String, Object> data) {
}
