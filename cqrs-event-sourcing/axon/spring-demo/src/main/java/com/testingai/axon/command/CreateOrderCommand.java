package com.testingai.axon.command;

public record CreateOrderCommand(String orderId, String customerId) {
}
