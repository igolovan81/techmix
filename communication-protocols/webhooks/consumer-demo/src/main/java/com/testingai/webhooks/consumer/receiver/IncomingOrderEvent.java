package com.testingai.webhooks.consumer.receiver;

import java.time.Instant;
import java.util.Map;

public record IncomingOrderEvent(String eventType, String orderId, Instant occurredAt, Map<String, Object> data) {
}
