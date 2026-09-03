package com.testingai.jooq.transactions;

import java.util.List;

public record PlaceOrderRequest(Long customerId, List<OrderLineRequest> lines) {
}
