package com.testingai.disruptor.errors.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSimulatorTest {

	@Test
	void maybeThrowFailsWithinExpectedRateBand() {
		int failures = 0;
		for (int i = 0; i < 1000; i++) {
			try {
				FailureSimulator.maybeThrow("test");
			} catch (RuntimeException ignored) {
				failures++;
			}
		}

		// With a 5% failure rate, expect roughly 50 failures; accept a 5-200 range to avoid flakiness.
		assertThat(failures).isBetween(5, 200);
	}
}
