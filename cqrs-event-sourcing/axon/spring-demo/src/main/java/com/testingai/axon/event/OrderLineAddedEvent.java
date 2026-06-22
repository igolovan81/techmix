package com.testingai.axon.event;

import java.math.BigDecimal;

public record OrderLineAddedEvent(String orderId, String productId, int quantity, BigDecimal price) {
}
