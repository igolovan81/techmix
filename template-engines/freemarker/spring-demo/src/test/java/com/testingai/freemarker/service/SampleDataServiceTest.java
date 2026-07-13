package com.testingai.freemarker.service;

import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDataServiceTest {

	private final SampleDataService service = new SampleDataService();

	@Test
	void findAllProducts_shouldReturnFourSeededProducts() {
		List<Product> products = service.findAllProducts();

		assertThat(products).hasSize(4);
		assertThat(products).extracting(Product::id).containsExactly("p1", "p2", "p3", "p4");
	}

	@Test
	void findOrder_shouldReturnOrderWhenPresent() {
		Optional<Order> order = service.findOrder("o1");

		assertThat(order).isPresent();
		assertThat(order.get().customer()).isEqualTo("Alice");
	}

	@Test
	void findOrder_shouldReturnEmptyWhenMissing() {
		assertThat(service.findOrder("missing")).isEmpty();
	}

	@Test
	void findAllOrders_shouldReturnTwoSeededOrders() {
		assertThat(service.findAllOrders()).hasSize(2);
	}

	@Test
	void secondSeededOrder_shouldHaveNullStatusForNullSafetyDemos() {
		Order order = service.findOrder("o2").orElseThrow();

		assertThat(order.status()).isNull();
	}
}
