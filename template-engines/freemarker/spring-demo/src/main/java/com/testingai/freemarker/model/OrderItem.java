package com.testingai.freemarker.model;

import java.math.BigDecimal;

public record OrderItem(String productId, String productName, int quantity, BigDecimal lineTotal) {
}
