package com.testingai.cassandra.counter;

import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCountService {

	private static final String INCREMENT = "UPDATE order_counts_by_product SET order_count = order_count + 1 WHERE product_id = ?";
	private static final String SELECT = "SELECT order_count FROM order_counts_by_product WHERE product_id = ?";

	private final CqlSession cqlSession;

	public void increment(UUID productId) {
		cqlSession.execute(SimpleStatement.newInstance(INCREMENT, productId));
	}

	public long getCount(UUID productId) {
		ResultSet resultSet = cqlSession.execute(SimpleStatement.newInstance(SELECT, productId));
		Row row = resultSet.one();
		return row == null ? 0L : row.getLong("order_count");
	}
}
