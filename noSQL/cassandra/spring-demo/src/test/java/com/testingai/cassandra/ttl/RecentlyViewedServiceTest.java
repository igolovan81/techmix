package com.testingai.cassandra.ttl;

import java.util.List;
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
class RecentlyViewedServiceTest {

	@InjectMocks
	private RecentlyViewedService recentlyViewedService;

	@Mock
	private CqlSession cqlSession;

	@Mock
	private ResultSet resultSet;

	@Mock
	private Row row;

	@Test
	void recordView_shouldInsertWithTtl() {
		UUID productId = UUID.randomUUID();

		recentlyViewedService.recordView(productId);

		verify(cqlSession).execute(any(Statement.class));
	}

	@Test
	void listViews_shouldReturnLiveRows() {
		UUID productId = UUID.randomUUID();
		UUID viewedAt = UUID.randomUUID();
		when(cqlSession.execute(any(Statement.class))).thenReturn(resultSet);
		when(resultSet.all()).thenReturn(List.of(row));
		when(row.getUuid("product_id")).thenReturn(productId);
		when(row.getUuid("viewed_at")).thenReturn(viewedAt);

		List<ProductView> result = recentlyViewedService.listViews(productId);

		assertThat(result).containsExactly(new ProductView(productId, viewedAt));
	}
}
