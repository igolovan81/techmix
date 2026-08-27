package com.testingai.cassandra.consistency;

import java.util.UUID;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.testingai.cassandra.crud.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConsistencyDemoService {

	private static final String SELECT = "SELECT id, name, price, stock FROM products WHERE id = ?";

	private final CqlSession cqlSession;

	public ConsistencyReadResult readAt(UUID productId, String consistencyLevel) {
		ConsistencyLevel level = parseConsistencyLevel(consistencyLevel);

		long start = System.currentTimeMillis();
		SimpleStatement statement = SimpleStatement.newInstance(SELECT, productId).setConsistencyLevel(level);
		ResultSet resultSet = cqlSession.execute(statement);
		Row row = resultSet.one();
		long elapsed = System.currentTimeMillis() - start;

		if (row == null) {
			throw new IllegalArgumentException("Product not found: " + productId);
		}

		Product product = new Product(row.getUuid("id"), row.getString("name"), row.getBigDecimal("price"),
				row.getInt("stock"));
		return new ConsistencyReadResult(product, consistencyLevel, elapsed);
	}

	private ConsistencyLevel parseConsistencyLevel(String consistencyLevel) {
		try {
			return DefaultConsistencyLevel.valueOf(consistencyLevel);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unsupported consistency level: " + consistencyLevel, e);
		}
	}
}
