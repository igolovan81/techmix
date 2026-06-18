package com.testingai.mongodb.crud;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final MongoTemplate mongoTemplate;

	public Product create(Product product) {
		return mongoTemplate.save(product);
	}

	public Product findById(String id) {
		return mongoTemplate.findById(id, Product.class);
	}

	public Product update(String id, Product updated) {
		updated.setId(id);
		return mongoTemplate.save(updated);
	}

	public void delete(String id) {
		mongoTemplate.remove(new Query(where("_id").is(id)), Product.class);
	}
}
