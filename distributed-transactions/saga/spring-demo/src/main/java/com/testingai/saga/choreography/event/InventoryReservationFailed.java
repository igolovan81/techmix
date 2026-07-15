package com.testingai.saga.choreography.event;

public record InventoryReservationFailed(String orderId, String reason) {
}
