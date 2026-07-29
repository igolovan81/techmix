package com.testingai.camunda.domain;

import java.util.List;

public record CheckoutRequest(String customerId, List<OrderLine> items, OrderStep failAt) {
}
