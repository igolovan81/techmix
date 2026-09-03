package com.testingai.jooq.search;

import java.math.BigDecimal;

public record ProductSearchCriteria(Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStockOnly,
		String nameContains) {
}
