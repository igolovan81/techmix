package com.testingai.saga.choreography.event;

import com.testingai.saga.domain.SagaStep;

public record PaymentProcessed(String orderId, SagaStep failAt) {
}
