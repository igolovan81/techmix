package com.testingai.disruptor.domain;

import java.math.BigDecimal;
import java.util.List;

public final class OrderGenerator {

	private static final List<String> SYMBOLS = List.of("AAPL", "MSFT", "GOOG");

	private OrderGenerator() {
	}

	public static Order generate(long index) {
		String symbol = SYMBOLS.get((int) (index % SYMBOLS.size()));
		Side side = index % 2 == 0 ? Side.BUY : Side.SELL;
		int quantity = 10 + (int) (index % 10);
		BigDecimal price = BigDecimal.valueOf(100 + (index % 5));
		return new Order("order-" + index, symbol, side, quantity, price);
	}
}
