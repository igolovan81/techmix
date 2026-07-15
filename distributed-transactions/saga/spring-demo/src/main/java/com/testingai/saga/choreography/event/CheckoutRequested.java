package com.testingai.saga.choreography.event;

import com.testingai.saga.domain.OrderLine;
import com.testingai.saga.domain.SagaStep;

import java.util.List;

public record CheckoutRequested(String orderId, String customerId, List<OrderLine> items, SagaStep failAt) {
}
