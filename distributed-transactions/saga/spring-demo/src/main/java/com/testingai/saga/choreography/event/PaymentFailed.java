package com.testingai.saga.choreography.event;

public record PaymentFailed(String orderId, String reason) {
}
