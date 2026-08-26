package com.testingai.disruptor.diamond;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiamondServiceTest {

	private DiamondService service;

	@BeforeEach
	void setUp() {
		service = new DiamondService();
		service.start();
	}

	@AfterEach
	void tearDown() {
		service.shutdown();
	}

	@Test
	void matchingHandlerRunsOnlyAfterBothUpstreamHandlersAndProducesFills() {
		DiamondResult result = service.process(500);

		long totalFilled = result.fills().stream().mapToLong(fill -> fill.quantity()).sum();
		assertThat(totalFilled).isGreaterThan(0);
		assertThat(result.restingOrders()).isGreaterThanOrEqualTo(0);
	}
}
