package com.testingai.graphql.domain;

public record Order(Long id, Long userId, OrderStatus status, String placedAt, long totalCents) {
}
