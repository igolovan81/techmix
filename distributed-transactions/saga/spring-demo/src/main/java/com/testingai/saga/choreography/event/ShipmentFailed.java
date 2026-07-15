package com.testingai.saga.choreography.event;

public record ShipmentFailed(String orderId, String reason) {
}
