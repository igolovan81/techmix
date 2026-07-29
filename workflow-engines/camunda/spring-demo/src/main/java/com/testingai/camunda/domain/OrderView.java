package com.testingai.camunda.domain;

import java.util.List;

public record OrderView(String orderId, long processInstanceKey, OrderStatus status, List<OrderStep> completedSteps) {
}
