package com.testingai.grpc.client.dto;

public record OrderStatusUpdateDto(String orderId, String status) {
}
