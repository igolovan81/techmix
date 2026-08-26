package com.testingai.disruptor.single;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SingleHandlerServiceTest {

	private SingleHandlerService service;

	@BeforeEach
	void setUp() {
		service = new SingleHandlerService();
		service.start();
	}

	@AfterEach
	void tearDown() {
		service.shutdown();
	}

	@Test
	void processesAllPublishedEvents() {
		SingleHandlerResult result = service.process(500);

		assertThat(result.eventsProcessed()).isEqualTo(500);
		assertThat(result.throughputPerSecond()).isGreaterThan(0);
	}
}
