package com.testingai.reactor.domain;

public record ProductWithDiscount(Product product, long discountedPriceCents) {
}
