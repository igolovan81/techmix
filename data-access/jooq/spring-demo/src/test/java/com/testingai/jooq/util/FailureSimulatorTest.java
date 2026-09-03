package com.testingai.jooq.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureSimulatorTest {

	@Test
	void maybeThrowDoesNotThrowMostOfTheTime() {
		int failures = 0;
		for (int i = 0; i < 1000; i++) {
			try {
				FailureSimulator.maybeThrow("test");
			} catch (RuntimeException ignored) {
				failures++;
			}
		}

		// With a 5% failure rate, expect roughly 50 failures across 1000 trials; accept a 5-200 range,
		// matching message-brokers/kafka's FailureSimulatorTest.groovy convention.
		assertThat(failures).isBetween(5, 200);
	}
}
