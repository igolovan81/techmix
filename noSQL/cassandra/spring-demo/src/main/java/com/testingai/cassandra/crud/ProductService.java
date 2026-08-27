package com.testingai.cassandra.crud;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.stereotype.Service;

import static org.springframework.data.cassandra.core.query.Criteria.where;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final CassandraTemplate cassandraTemplate;

	public Product create(Product product) {
		if (product.getId() == null) {
			product.setId(UUID.randomUUID());
		}
		return cassandraTemplate.insert(product);
	}

	public Product findById(UUID id) {
		return cassandraTemplate.selectOneById(id, Product.class);
	}

	public Product update(UUID id, Product updated) {
		updated.setId(id);
		return cassandraTemplate.update(updated);
	}

	public void delete(UUID id) {
		cassandraTemplate.delete(Query.query(where("id").is(id)), Product.class);
	}
}
