package com.testingai.disruptor.domain;

import com.lmax.disruptor.EventTranslatorOneArg;

import java.math.BigDecimal;

public class OrderEvent {

	public static final EventTranslatorOneArg<OrderEvent, Order> TRANSLATOR = (event, sequence, order) -> event
			.set(order);

	private String orderId;
	private String symbol;
	private Side side;
	private int quantity;
	private BigDecimal price;
	private long publishNanos;

	public void set(Order order) {
		this.orderId = order.orderId();
		this.symbol = order.symbol();
		this.side = order.side();
		this.quantity = order.quantity();
		this.price = order.price();
		this.publishNanos = System.nanoTime();
	}

	public String getOrderId() {
		return orderId;
	}

	public String getSymbol() {
		return symbol;
	}

	public Side getSide() {
		return side;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public long getPublishNanos() {
		return publishNanos;
	}
}
