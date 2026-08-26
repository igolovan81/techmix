package com.testingai.disruptor.matching;

import com.testingai.disruptor.domain.Side;

import java.math.BigDecimal;

final class RestingOrder {

	private final String orderId;
	private final String symbol;
	private final Side side;
	private final BigDecimal price;
	private int quantity;

	RestingOrder(String orderId, String symbol, Side side, int quantity, BigDecimal price) {
		this.orderId = orderId;
		this.symbol = symbol;
		this.side = side;
		this.quantity = quantity;
		this.price = price;
	}

	String orderId() {
		return orderId;
	}

	int quantity() {
		return quantity;
	}

	void reduceQuantity(int filled) {
		this.quantity -= filled;
	}
}
