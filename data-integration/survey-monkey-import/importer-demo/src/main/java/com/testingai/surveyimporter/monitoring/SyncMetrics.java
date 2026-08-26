package com.testingai.surveyimporter.monitoring;

import com.testingai.surveyimporter.dlq.DeadLetterJobRepository;
import com.testingai.surveyimporter.entity.SyncWatermarkEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class SyncMetrics {

	private final Counter processedSuccess;
	private final Counter processedRetried;
	private final Counter processedDeadLettered;

	public SyncMetrics(MeterRegistry meterRegistry, JobQueue jobQueue, DeadLetterJobRepository dlqRepository,
			SyncWatermarkRepository watermarkRepository, List<String> knownSurveyIds) {
		this.processedSuccess = Counter.builder("sync.jobs.processed").tag("outcome", "success")
				.register(meterRegistry);
		this.processedRetried = Counter.builder("sync.jobs.processed").tag("outcome", "retried")
				.register(meterRegistry);
		this.processedDeadLettered = Counter.builder("sync.jobs.processed").tag("outcome", "dead_lettered")
				.register(meterRegistry);
		Gauge.builder("sync.queue.depth", jobQueue, JobQueue::size).register(meterRegistry);
		Gauge.builder("sync.dlq.size", dlqRepository, DeadLetterJobRepository::count).register(meterRegistry);
		for (String surveyId : knownSurveyIds) {
			Gauge.builder("sync.lag.seconds", watermarkRepository, repo -> lagSeconds(repo, surveyId))
					.tag("survey_id", surveyId).register(meterRegistry);
		}
	}

	public void recordProcessed(String outcome) {
		switch (outcome) {
			case "success" -> processedSuccess.increment();
			case "retried" -> processedRetried.increment();
			case "dead_lettered" -> processedDeadLettered.increment();
			default -> throw new IllegalArgumentException("Unknown outcome: " + outcome);
		}
	}

	private static double lagSeconds(SyncWatermarkRepository repository, String surveyId) {
		return repository.findById(surveyId).map(SyncWatermarkEntity::getLastSyncedAt)
				.map(lastSyncedAt -> (double) Duration.between(lastSyncedAt, Instant.now()).toSeconds()).orElse(-1.0);
	}
}
