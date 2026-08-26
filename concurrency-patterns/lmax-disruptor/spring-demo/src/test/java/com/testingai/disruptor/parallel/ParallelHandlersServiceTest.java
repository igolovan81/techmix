package com.testingai.disruptor.parallel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelHandlersServiceTest {

	private ParallelHandlersService service;

	@BeforeEach
	void setUp() {
		service = new ParallelHandlersService();
		service.start();
	}

	@AfterEach
	void tearDown() {
		service.shutdown();
	}

	@Test
	void bothHandlersProcessEveryEventIndependently() {
		ParallelResult result = service.process(500);

		assertThat(result.journalCount()).isEqualTo(500);
		assertThat(result.riskCheckCount()).isEqualTo(500);
	}

	@Test
	void countersResetBetweenRuns() {
		service.process(500);
		ParallelResult second = service.process(200);

		assertThat(second.journalCount()).isEqualTo(200);
		assertThat(second.riskCheckCount()).isEqualTo(200);
	}
}
