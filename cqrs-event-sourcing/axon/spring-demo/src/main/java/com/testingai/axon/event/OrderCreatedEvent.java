package com.testingai.axon.event;

public record OrderCreatedEvent(String orderId, String customerId) {
}
