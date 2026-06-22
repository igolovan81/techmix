package com.testingai.axon.query;

public record OrderSummary(String orderId, String customerId, int lineCount, String status) {
}
