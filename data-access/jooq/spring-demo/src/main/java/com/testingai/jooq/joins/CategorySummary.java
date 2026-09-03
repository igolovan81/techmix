package com.testingai.jooq.joins;

import java.math.BigDecimal;

public record CategorySummary(Long categoryId, String categoryName, int productCount, int totalStock,
		BigDecimal avgPrice) {
}
