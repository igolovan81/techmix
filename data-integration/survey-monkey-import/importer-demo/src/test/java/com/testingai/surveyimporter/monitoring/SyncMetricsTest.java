package com.testingai.surveyimporter.monitoring;

import com.testingai.surveyimporter.dlq.DeadLetterJobRepository;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncMetricsTest {

	@Test
	void recordProcessedIncrementsTheCounterForTheGivenOutcome() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		JobQueue jobQueue = new JobQueue();
		DeadLetterJobRepository dlqRepository = mock(DeadLetterJobRepository.class);
		when(dlqRepository.count()).thenReturn(0L);
		SyncWatermarkRepository watermarkRepository = mock(SyncWatermarkRepository.class);
		SyncMetrics metrics = new SyncMetrics(registry, jobQueue, dlqRepository, watermarkRepository,
				List.of("survey-1"));

		metrics.recordProcessed("success");
		metrics.recordProcessed("success");
		metrics.recordProcessed("retried");

		assertThat(registry.get("sync.jobs.processed").tag("outcome", "success").counter().count()).isEqualTo(2.0);
		assertThat(registry.get("sync.jobs.processed").tag("outcome", "retried").counter().count()).isEqualTo(1.0);
	}

	@Test
	void rejectsAnUnknownOutcome() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		JobQueue jobQueue = new JobQueue();
		DeadLetterJobRepository dlqRepository = mock(DeadLetterJobRepository.class);
		when(dlqRepository.count()).thenReturn(0L);
		SyncWatermarkRepository watermarkRepository = mock(SyncWatermarkRepository.class);
		SyncMetrics metrics = new SyncMetrics(registry, jobQueue, dlqRepository, watermarkRepository, List.of());

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> metrics.recordProcessed("unknown"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
