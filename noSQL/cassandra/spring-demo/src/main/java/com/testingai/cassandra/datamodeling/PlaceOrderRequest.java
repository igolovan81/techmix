package com.testingai.cassandra.datamodeling;

import java.util.UUID;

public record PlaceOrderRequest(String customerId, UUID productId, int quantity) {
}
