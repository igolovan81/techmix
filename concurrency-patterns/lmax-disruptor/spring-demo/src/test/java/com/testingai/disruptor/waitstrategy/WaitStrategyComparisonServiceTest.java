package com.testingai.disruptor.waitstrategy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaitStrategyComparisonServiceTest {

	private final WaitStrategyComparisonService service = new WaitStrategyComparisonService();

	@Test
	void comparesAllThreeWaitStrategies() {
		List<WaitStrategyStat> stats = service.compare(200);

		assertThat(stats).hasSize(3);
		assertThat(stats).extracting(WaitStrategyStat::strategyName).containsExactlyInAnyOrder("BLOCKING", "YIELDING",
				"BUSY_SPIN");
		assertThat(stats).allSatisfy(stat -> assertThat(stat.eventsProcessed()).isEqualTo(200));
	}
}
