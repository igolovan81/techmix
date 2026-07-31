package com.testingai.graphql.domain;

import java.util.List;

public record PlaceOrderInput(List<OrderItemInput> items) {
}
