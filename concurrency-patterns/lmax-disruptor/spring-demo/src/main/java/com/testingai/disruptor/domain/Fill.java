package com.testingai.disruptor.domain;

import java.math.BigDecimal;

public record Fill(String symbol, String buyOrderId, String sellOrderId, int quantity, BigDecimal price) {
}
