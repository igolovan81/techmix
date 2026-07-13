package com.testingai.handlebars.service;

import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.OrderItem;
import com.testingai.handlebars.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SampleDataService {

	private final List<Product> products = List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100),
			new Product("p2", "Gadget", new BigDecimal("19.99"), 50),
			new Product("p3", "Gizmo", new BigDecimal("29.99"), 0),
			new Product("p4", "Doohickey", new BigDecimal("4.99"), 200));

	private final List<Order> orders = List.of(
			new Order("o1", "Alice",
					List.of(new OrderItem("p1", "Widget", 2, new BigDecimal("19.98")),
							new OrderItem("p2", "Gadget", 1, new BigDecimal("19.99"))),
					new BigDecimal("39.97"), "CONFIRMED", Instant.parse("2026-07-01T10:15:30Z")),
			new Order("o2", "Bob", List.of(new OrderItem("p4", "Doohickey", 3, new BigDecimal("14.97"))),
					new BigDecimal("14.97"), null, Instant.parse("2026-07-05T08:00:00Z")));

	public List<Product> findAllProducts() {
		return products;
	}

	public Optional<Order> findOrder(String id) {
		return orders.stream().filter(order -> order.id().equals(id)).findFirst();
	}

	public List<Order> findAllOrders() {
		return orders;
	}
}
