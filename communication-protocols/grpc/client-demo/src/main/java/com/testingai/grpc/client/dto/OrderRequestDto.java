package com.testingai.grpc.client.dto;

public record OrderRequestDto(String productId, int quantity) {
}
