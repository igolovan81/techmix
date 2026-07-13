package com.testingai.handlebars.model;

import java.math.BigDecimal;

public record OrderItem(String productId, String productName, int quantity, BigDecimal lineTotal) {
}
