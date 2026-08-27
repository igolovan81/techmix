package com.testingai.cassandra.lwt;

import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.testingai.cassandra.crud.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockReservationService {

	private static final String CAS_UPDATE = "UPDATE products SET stock = ? WHERE id = ? IF stock = ?";

	private final CassandraTemplate cassandraTemplate;
	private final CqlSession cqlSession;

	public Product decrementIfAvailable(UUID productId, int quantity) {
		Product product = cassandraTemplate.selectOneById(productId, Product.class);
		if (product == null) {
			throw new IllegalArgumentException("Product not found: " + productId);
		}
		if (product.getStock() < quantity) {
			throw new IllegalStateException("Insufficient stock for product: " + productId);
		}

		int newStock = product.getStock() - quantity;
		SimpleStatement statement = SimpleStatement.newInstance(CAS_UPDATE, newStock, productId, product.getStock());
		ResultSet resultSet = cqlSession.execute(statement);
		if (!resultSet.wasApplied()) {
			throw new IllegalStateException("Concurrent modification detected for product: " + productId);
		}

		return product;
	}
}
