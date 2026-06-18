package com.testingai.mongodb.transaction;

import com.testingai.mongodb.crud.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@InjectMocks
	private OrderService orderService;

	@Mock
	private MongoTemplate mongoTemplate;

	@Test
	void placeOrder_shouldDecrementStockAndInsertOrder() {
		Product product = new Product("p1", "Widget", 10.0, 5);
		when(mongoTemplate.findById("p1", Product.class)).thenReturn(product);
		when(mongoTemplate.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(mongoTemplate.insert(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Order order = orderService.placeOrder("p1", 3);

		assertThat(order.getProductId()).isEqualTo("p1");
		assertThat(order.getQuantity()).isEqualTo(3);
		assertThat(order.getUnitPrice()).isEqualTo(10.0);
		assertThat(order.getLineTotal()).isEqualTo(30.0);
		assertThat(order.getStatus()).isEqualTo("PLACED");
		assertThat(product.getStock()).isEqualTo(2);
		verify(mongoTemplate).save(product);
		verify(mongoTemplate).insert(any(Order.class));
	}

	@Test
	void placeOrder_shouldThrowAndNotPersistWhenProductMissing() {
		when(mongoTemplate.findById("missing", Product.class)).thenReturn(null);

		assertThatThrownBy(() -> orderService.placeOrder("missing", 1)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("missing");

		verify(mongoTemplate, never()).save(any(Product.class));
		verify(mongoTemplate, never()).insert(any(Order.class));
	}

	@Test
	void placeOrder_shouldThrowAndNotPersistWhenStockInsufficient() {
		Product product = new Product("p1", "Widget", 10.0, 2);
		when(mongoTemplate.findById("p1", Product.class)).thenReturn(product);

		assertThatThrownBy(() -> orderService.placeOrder("p1", 5)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("p1");

		verify(mongoTemplate, never()).save(any(Product.class));
		verify(mongoTemplate, never()).insert(any(Order.class));
	}
}
