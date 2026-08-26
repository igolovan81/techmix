package com.testingai.disruptor.matching;

import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.domain.Order;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.Side;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMatchingEngineTest {

	private final OrderMatchingEngine engine = new OrderMatchingEngine();

	private OrderEvent eventFor(Order order) {
		OrderEvent event = new OrderEvent();
		event.set(order);
		return event;
	}

	@Test
	void nonCrossingOrderRestsInTheBook() {
		Order sellOrder = new Order("sell-1", "AAPL", Side.SELL, 10, BigDecimal.valueOf(105));

		List<Fill> fills = engine.match(eventFor(sellOrder));

		assertThat(fills).isEmpty();
		assertThat(engine.restingOrderCount()).isEqualTo(1);
	}

	@Test
	void crossingOrderProducesAFill() {
		engine.match(eventFor(new Order("sell-1", "AAPL", Side.SELL, 10, BigDecimal.valueOf(100))));

		List<Fill> fills = engine.match(eventFor(new Order("buy-1", "AAPL", Side.BUY, 10, BigDecimal.valueOf(101))));

		assertThat(fills).hasSize(1);
		Fill fill = fills.get(0);
		assertThat(fill.buyOrderId()).isEqualTo("buy-1");
		assertThat(fill.sellOrderId()).isEqualTo("sell-1");
		assertThat(fill.quantity()).isEqualTo(10);
		assertThat(fill.price()).isEqualByComparingTo(BigDecimal.valueOf(100));
		assertThat(engine.restingOrderCount()).isZero();
	}

	@Test
	void partialFillLeavesRemainderResting() {
		engine.match(eventFor(new Order("sell-1", "AAPL", Side.SELL, 4, BigDecimal.valueOf(100))));

		List<Fill> fills = engine.match(eventFor(new Order("buy-1", "AAPL", Side.BUY, 10, BigDecimal.valueOf(100))));

		assertThat(fills).hasSize(1);
		assertThat(fills.get(0).quantity()).isEqualTo(4);
		assertThat(engine.restingOrderCount()).isEqualTo(1);
	}

	@Test
	void ordersAtSamePriceMatchInTimePriorityOrder() {
		engine.match(eventFor(new Order("sell-1", "AAPL", Side.SELL, 5, BigDecimal.valueOf(100))));
		engine.match(eventFor(new Order("sell-2", "AAPL", Side.SELL, 5, BigDecimal.valueOf(100))));

		List<Fill> fills = engine.match(eventFor(new Order("buy-1", "AAPL", Side.BUY, 5, BigDecimal.valueOf(100))));

		assertThat(fills).hasSize(1);
		assertThat(fills.get(0).sellOrderId()).isEqualTo("sell-1");
		assertThat(engine.restingOrderCount()).isEqualTo(1);
	}
}
