package com.testingai.saga.choreography.event;

public record OrderCancelled(String orderId, String reason) {
}
