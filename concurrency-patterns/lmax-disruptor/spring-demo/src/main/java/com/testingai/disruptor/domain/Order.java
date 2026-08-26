package com.testingai.disruptor.domain;

import java.math.BigDecimal;

public record Order(String orderId, String symbol, Side side, int quantity, BigDecimal price) {
}
