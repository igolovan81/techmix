package com.testingai.saga.choreography.event;

import com.testingai.saga.domain.SagaStep;

public record InventoryReserved(String orderId, SagaStep failAt) {
}
