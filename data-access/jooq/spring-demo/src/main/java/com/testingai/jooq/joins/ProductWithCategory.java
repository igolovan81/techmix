package com.testingai.jooq.joins;

import java.math.BigDecimal;

public record ProductWithCategory(Long id, String name, BigDecimal price, String categoryName) {
}
