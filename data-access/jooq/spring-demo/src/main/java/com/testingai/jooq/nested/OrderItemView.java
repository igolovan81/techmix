package com.testingai.jooq.nested;

import java.math.BigDecimal;

public record OrderItemView(Long productId, String productName, Integer quantity, BigDecimal unitPrice) {
}
