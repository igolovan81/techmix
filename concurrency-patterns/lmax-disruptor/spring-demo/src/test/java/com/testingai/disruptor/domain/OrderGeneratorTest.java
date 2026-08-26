package com.testingai.disruptor.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderGeneratorTest {

	@Test
	void generatesDeterministicAlternatingBuySellOrders() {
		Order first = OrderGenerator.generate(0);
		Order second = OrderGenerator.generate(1);

		assertThat(first.side()).isEqualTo(Side.BUY);
		assertThat(second.side()).isEqualTo(Side.SELL);
		assertThat(first.orderId()).isEqualTo("order-0");
		assertThat(second.orderId()).isEqualTo("order-1");
	}

	@Test
	void generateIsPureAndRepeatable() {
		Order firstCall = OrderGenerator.generate(42);
		Order secondCall = OrderGenerator.generate(42);

		assertThat(firstCall).isEqualTo(secondCall);
	}
}
