package com.testingai.cassandra.lwt;

import java.math.BigDecimal;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.testingai.cassandra.crud.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

	@InjectMocks
	private StockReservationService stockReservationService;

	@Mock
	private CassandraTemplate cassandraTemplate;

	@Mock
	private CqlSession cqlSession;

	@Mock
	private ResultSet resultSet;

	@Test
	void decrementIfAvailable_shouldApplyCasWriteWhenStockSufficient() {
		UUID id = UUID.randomUUID();
		Product product = new Product(id, "Widget", new BigDecimal("9.99"), 10);
		when(cassandraTemplate.selectOneById(id, Product.class)).thenReturn(product);
		when(cqlSession.execute(any(com.datastax.oss.driver.api.core.cql.Statement.class))).thenReturn(resultSet);
		when(resultSet.wasApplied()).thenReturn(true);

		Product result = stockReservationService.decrementIfAvailable(id, 4);

		assertThat(result).isEqualTo(product);
	}

	@Test
	void decrementIfAvailable_shouldThrowWhenProductMissing() {
		UUID id = UUID.randomUUID();
		when(cassandraTemplate.selectOneById(id, Product.class)).thenReturn(null);

		assertThatThrownBy(() -> stockReservationService.decrementIfAvailable(id, 1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void decrementIfAvailable_shouldThrowWhenStockInsufficient() {
		UUID id = UUID.randomUUID();
		Product product = new Product(id, "Widget", new BigDecimal("9.99"), 2);
		when(cassandraTemplate.selectOneById(id, Product.class)).thenReturn(product);

		assertThatThrownBy(() -> stockReservationService.decrementIfAvailable(id, 5))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void decrementIfAvailable_shouldThrowWhenCasWriteLost() {
		UUID id = UUID.randomUUID();
		Product product = new Product(id, "Widget", new BigDecimal("9.99"), 10);
		when(cassandraTemplate.selectOneById(id, Product.class)).thenReturn(product);
		when(cqlSession.execute(any(com.datastax.oss.driver.api.core.cql.Statement.class))).thenReturn(resultSet);
		when(resultSet.wasApplied()).thenReturn(false);

		assertThatThrownBy(() -> stockReservationService.decrementIfAvailable(id, 4))
				.isInstanceOf(IllegalStateException.class);
	}
}
