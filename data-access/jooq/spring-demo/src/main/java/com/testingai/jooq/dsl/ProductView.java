package com.testingai.jooq.dsl;

import java.math.BigDecimal;

public record ProductView(Long id, Long categoryId, String name, BigDecimal price, Integer stock) {
}
