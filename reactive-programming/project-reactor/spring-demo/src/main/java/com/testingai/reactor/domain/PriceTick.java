package com.testingai.reactor.domain;

import java.time.Instant;

public record PriceTick(String productId, long priceCents, Instant timestamp) {
}
