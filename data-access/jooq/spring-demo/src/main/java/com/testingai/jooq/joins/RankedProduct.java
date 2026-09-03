package com.testingai.jooq.joins;

import java.math.BigDecimal;

public record RankedProduct(Long id, String name, BigDecimal price, Long categoryId, Integer rank) {
}
