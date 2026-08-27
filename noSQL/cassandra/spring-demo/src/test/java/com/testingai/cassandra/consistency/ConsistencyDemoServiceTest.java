package com.testingai.cassandra.consistency;

import java.math.BigDecimal;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsistencyDemoServiceTest {

	@InjectMocks
	private ConsistencyDemoService consistencyDemoService;

	@Mock
	private CqlSession cqlSession;

	@Mock
	private ResultSet resultSet;

	@Mock
	private Row row;

	@Test
	void readAt_shouldReadProductAtRequestedConsistencyLevel() {
		UUID id = UUID.randomUUID();
		when(cqlSession.execute(any(Statement.class))).thenReturn(resultSet);
		when(resultSet.one()).thenReturn(row);
		when(row.getUuid("id")).thenReturn(id);
		when(row.getString("name")).thenReturn("Widget");
		when(row.getBigDecimal("price")).thenReturn(new BigDecimal("9.99"));
		when(row.getInt("stock")).thenReturn(100);

		ConsistencyReadResult result = consistencyDemoService.readAt(id, "QUORUM");

		assertThat(result.product().getId()).isEqualTo(id);
		assertThat(result.product().getName()).isEqualTo("Widget");
		assertThat(result.consistencyLevel()).isEqualTo("QUORUM");
	}

	@Test
	void readAt_shouldThrowWhenProductMissing() {
		UUID id = UUID.randomUUID();
		when(cqlSession.execute(any(Statement.class))).thenReturn(resultSet);
		when(resultSet.one()).thenReturn(null);

		assertThatThrownBy(() -> consistencyDemoService.readAt(id, "ONE")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void readAt_shouldRejectUnknownConsistencyLevel() {
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> consistencyDemoService.readAt(id, "NOT_A_LEVEL"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
