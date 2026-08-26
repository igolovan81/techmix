package com.testingai.surveyimporter.monitoring;

import com.testingai.surveyimporter.dlq.DeadLetterJobRepository;
import com.testingai.surveyimporter.entity.SyncWatermarkEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/demo")
public class DemoStatusController {

	private final SyncWatermarkRepository watermarkRepository;
	private final JobQueue jobQueue;
	private final DeadLetterJobRepository dlqRepository;
	private final CircuitBreakerRegistry circuitBreakerRegistry;
	private final List<String> knownSurveyIds;

	public DemoStatusController(SyncWatermarkRepository watermarkRepository, JobQueue jobQueue,
			DeadLetterJobRepository dlqRepository, CircuitBreakerRegistry circuitBreakerRegistry,
			@Value("#{'${importer.known-survey-ids}'.split(',')}") List<String> knownSurveyIds) {
		this.watermarkRepository = watermarkRepository;
		this.jobQueue = jobQueue;
		this.dlqRepository = dlqRepository;
		this.circuitBreakerRegistry = circuitBreakerRegistry;
		this.knownSurveyIds = knownSurveyIds;
	}

	@GetMapping("/status")
	public DemoStatusResponse status() {
		List<SurveyStatus> surveys = knownSurveyIds.stream().map(this::statusFor).toList();
		String breakerState = circuitBreakerRegistry.circuitBreaker("surveyMonkey").getState().name();
		return new DemoStatusResponse(surveys, jobQueue.size(), dlqRepository.count(), breakerState);
	}

	private SurveyStatus statusFor(String surveyId) {
		return watermarkRepository.findById(surveyId).map(SyncWatermarkEntity::getLastSyncedAt)
				.map(lastSyncedAt -> new SurveyStatus(surveyId, lastSyncedAt,
						Duration.between(lastSyncedAt, Instant.now()).toSeconds()))
				.orElse(new SurveyStatus(surveyId, null, null));
	}
}
