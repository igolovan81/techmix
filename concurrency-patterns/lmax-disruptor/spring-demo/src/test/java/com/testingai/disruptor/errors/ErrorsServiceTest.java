package com.testingai.disruptor.errors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorsServiceTest {

	private ErrorsService service;

	@BeforeEach
	void setUp() {
		service = new ErrorsService();
		service.start();
	}

	@AfterEach
	void tearDown() {
		service.shutdown();
	}

	@Test
	void ringBufferSurvivesSimulatedHandlerFailures() {
		ErrorsResult result = service.process(1000);

		assertThat(result.succeeded() + result.failed()).isEqualTo(1000);
		// With a 5% failure rate, expect roughly 50 failures; accept a wide band to avoid flakiness.
		assertThat(result.failed()).isBetween(5L, 200L);
	}
}
