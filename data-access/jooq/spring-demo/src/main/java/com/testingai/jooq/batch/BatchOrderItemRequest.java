package com.testingai.jooq.batch;

import java.math.BigDecimal;

public record BatchOrderItemRequest(Long productId, Integer quantity, BigDecimal unitPrice) {
}
