package com.testingai.jooq.transactions;

public record OrderLineRequest(Long productId, Integer quantity) {
}
