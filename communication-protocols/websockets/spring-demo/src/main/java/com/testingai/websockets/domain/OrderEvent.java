package com.testingai.websockets.domain;

import java.time.Instant;

public record OrderEvent(String orderId, OrderStatus status, Instant occurredAt) {
}
