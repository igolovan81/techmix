package com.testingai.cassandra.datamodeling;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.testingai.cassandra.counter.OrderCountService;
import com.testingai.cassandra.crud.Product;
import com.testingai.cassandra.lwt.StockReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@InjectMocks
	private OrderService orderService;

	@Mock
	private CassandraTemplate cassandraTemplate;

	@Mock
	private StockReservationService stockReservationService;

	@Mock
	private OrderCountService orderCountService;

	@Test
	void placeOrder_shouldReserveStockWriteBothTablesAndIncrementCounter() {
		UUID productId = UUID.randomUUID();
		Product product = new Product(productId, "Widget", new BigDecimal("10.00"), 20);
		when(stockReservationService.decrementIfAvailable(productId, 3)).thenReturn(product);
		when(cassandraTemplate.insert(any(OrderByCustomer.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(cassandraTemplate.insert(any(OrderByProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OrderByCustomer result = orderService.placeOrder("cust-1", productId, 3);

		assertThat(result.getCustomerId()).isEqualTo("cust-1");
		assertThat(result.getProductId()).isEqualTo(productId);
		assertThat(result.getQuantity()).isEqualTo(3);
		assertThat(result.getUnitPrice()).isEqualByComparingTo("10.00");
		assertThat(result.getLineTotal()).isEqualByComparingTo("30.00");
		assertThat(result.getOrderId()).isNotNull();
		verify(cassandraTemplate).insert(any(OrderByCustomer.class));
		verify(cassandraTemplate).insert(any(OrderByProduct.class));
		verify(orderCountService).increment(productId);
	}

	@Test
	void findByCustomer_shouldSelectFromOrdersByCustomer() {
		when(cassandraTemplate.select(any(Query.class), org.mockito.ArgumentMatchers.eq(OrderByCustomer.class)))
				.thenReturn(List.of());

		List<OrderByCustomer> result = orderService.findByCustomer("cust-1");

		assertThat(result).isEmpty();
	}

	@Test
	void findByProduct_shouldSelectFromOrdersByProduct() {
		UUID productId = UUID.randomUUID();
		when(cassandraTemplate.select(any(Query.class), org.mockito.ArgumentMatchers.eq(OrderByProduct.class)))
				.thenReturn(List.of());

		List<OrderByProduct> result = orderService.findByProduct(productId);

		assertThat(result).isEmpty();
	}
}
