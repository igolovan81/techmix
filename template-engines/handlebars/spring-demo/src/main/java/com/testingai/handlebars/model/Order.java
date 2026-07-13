package com.testingai.handlebars.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(String id, String customer, List<OrderItem> items, BigDecimal total, String status,
		Instant placedAt) {
}
