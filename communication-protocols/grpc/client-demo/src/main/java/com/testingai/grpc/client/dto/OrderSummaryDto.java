package com.testingai.grpc.client.dto;

public record OrderSummaryDto(int orderCount, long totalPriceCents) {
}
