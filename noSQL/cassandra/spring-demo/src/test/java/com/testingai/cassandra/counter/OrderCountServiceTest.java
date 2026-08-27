package com.testingai.cassandra.counter;

import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCountServiceTest {

	@InjectMocks
	private OrderCountService orderCountService;

	@Mock
	private CqlSession cqlSession;

	@Mock
	private ResultSet resultSet;

	@Mock
	private Row row;

	@Test
	void increment_shouldExecuteCounterUpdate() {
		UUID id = UUID.randomUUID();

		orderCountService.increment(id);

		verify(cqlSession).execute(any(Statement.class));
	}

	@Test
	void getCount_shouldReturnCounterValue() {
		UUID id = UUID.randomUUID();
		when(cqlSession.execute(any(Statement.class))).thenReturn(resultSet);
		when(resultSet.one()).thenReturn(row);
		when(row.getLong("order_count")).thenReturn(3L);

		long count = orderCountService.getCount(id);

		assertThat(count).isEqualTo(3L);
	}

	@Test
	void getCount_shouldReturnZeroWhenNoRow() {
		UUID id = UUID.randomUUID();
		when(cqlSession.execute(any(Statement.class))).thenReturn(resultSet);
		when(resultSet.one()).thenReturn(null);

		long count = orderCountService.getCount(id);

		assertThat(count).isZero();
	}
}
