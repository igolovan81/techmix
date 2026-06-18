package com.testingai.mongodb.transaction;

import com.testingai.mongodb.crud.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final MongoTemplate mongoTemplate;

	@Transactional
	public Order placeOrder(String productId, int quantity) {
		Product product = mongoTemplate.findById(productId, Product.class);
		if (product == null) {
			throw new IllegalArgumentException("Product not found: " + productId);
		}
		if (product.getStock() < quantity) {
			throw new IllegalStateException("Insufficient stock for product: " + productId);
		}

		product.setStock(product.getStock() - quantity);
		mongoTemplate.save(product);

		Order order = new Order(null, productId, quantity, product.getPrice(), product.getPrice() * quantity, "PLACED");
		return mongoTemplate.insert(order);
	}
}
