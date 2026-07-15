package com.testingai.saga.choreography.event;

import com.testingai.saga.domain.OrderLine;
import com.testingai.saga.domain.SagaStep;

import java.util.List;

public record OrderCreated(String orderId, List<OrderLine> items, SagaStep failAt) {
}
