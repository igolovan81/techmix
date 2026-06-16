package com.testingai.rabbitmq.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSimulatorTest {

	@Test
	void maybeThrow_shouldThrowRuntimeExceptionOccasionally() {
		int failures = 0;
		for (int i = 0; i < 500; i++) {
			try {
				FailureSimulator.maybeThrow("test");
			} catch (RuntimeException e) {
				failures++;
			}
		}
		assertThat(failures).isGreaterThan(0).isLessThan(500);
	}

	@Test
	void maybeThrow_shouldIncludeContextInMessage() {
		RuntimeException caught = null;
		for (int i = 0; i < 1000 && caught == null; i++) {
			try {
				FailureSimulator.maybeThrow("myContext");
			} catch (RuntimeException e) {
				caught = e;
			}
		}
		assertThat(caught).as("Expected at least one RuntimeException in 1000 calls at 5% failure rate").isNotNull();
		assertThat(caught.getMessage()).contains("myContext");
	}
}
