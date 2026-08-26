package com.testingai.disruptor.producer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProducerComparisonServiceTest {

	private final ProducerComparisonService service = new ProducerComparisonService();

	@Test
	void comparesSingleAndMultiProducerConfigurations() {
		List<ProducerStat> stats = service.compare(200, 4);

		assertThat(stats).hasSize(2);
		assertThat(stats.get(0).producerType()).isEqualTo("SINGLE");
		assertThat(stats.get(0).threadCount()).isEqualTo(1);
		assertThat(stats.get(0).eventsProcessed()).isEqualTo(200);
		assertThat(stats.get(1).producerType()).isEqualTo("MULTI");
		assertThat(stats.get(1).threadCount()).isEqualTo(4);
		assertThat(stats.get(1).eventsProcessed()).isEqualTo(200);
	}
}
