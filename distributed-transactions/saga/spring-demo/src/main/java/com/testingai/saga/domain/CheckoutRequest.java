package com.testingai.saga.domain;

import java.util.List;

public record CheckoutRequest(String customerId, List<OrderLine> items, SagaStep failAt) {
}
