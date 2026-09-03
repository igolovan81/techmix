package com.testingai.jooq.transactions;

public class InsufficientStockException extends RuntimeException {

	public InsufficientStockException(Long productId) {
		super("Insufficient stock for product " + productId);
	}
}
