package com.testingai.cassandra.ttl;

import java.util.List;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecentlyViewedService {

	private static final int TTL_SECONDS = 300;
	private static final String INSERT = "INSERT INTO recently_viewed (product_id, viewed_at) VALUES (?, ?) USING TTL "
			+ TTL_SECONDS;
	private static final String SELECT = "SELECT product_id, viewed_at FROM recently_viewed WHERE product_id = ?";

	private final CqlSession cqlSession;

	public void recordView(UUID productId) {
		cqlSession.execute(SimpleStatement.newInstance(INSERT, productId, Uuids.timeBased()));
	}

	public List<ProductView> listViews(UUID productId) {
		ResultSet resultSet = cqlSession.execute(SimpleStatement.newInstance(SELECT, productId));
		return resultSet.all().stream().map(row -> new ProductView(row.getUuid("product_id"), row.getUuid("viewed_at")))
				.toList();
	}
}
