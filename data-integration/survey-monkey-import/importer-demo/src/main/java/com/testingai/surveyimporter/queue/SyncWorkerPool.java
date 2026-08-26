package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.connector.ConnectorService;
import com.testingai.surveyimporter.dlq.DeadLetterService;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.monitoring.SyncMetrics;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SyncWorkerPool {

	private static final int WORKER_COUNT = 3;
	private static final int MAX_ATTEMPTS = 5;

	private final JobQueue jobQueue;
	private final ConnectorService connectorService;
	private final DeadLetterService deadLetterService;
	private final SyncMetrics syncMetrics;

	private ExecutorService executor;

	public SyncWorkerPool(JobQueue jobQueue, ConnectorService connectorService, DeadLetterService deadLetterService,
			SyncMetrics syncMetrics) {
		this.jobQueue = jobQueue;
		this.connectorService = connectorService;
		this.deadLetterService = deadLetterService;
		this.syncMetrics = syncMetrics;
	}

	@PostConstruct
	public void start() {
		executor = Executors.newFixedThreadPool(WORKER_COUNT);
		for (int i = 0; i < WORKER_COUNT; i++) {
			executor.submit(this::runLoop);
		}
	}

	@PreDestroy
	public void shutdown() {
		executor.shutdownNow();
	}

	private void runLoop() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				process(jobQueue.take());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public void process(SyncJob job) {
		try {
			connectorService.process(job);
			syncMetrics.recordProcessed("success");
		} catch (PermanentSyncException e) {
			deadLetterService.deadLetter(job, e);
			syncMetrics.recordProcessed("dead_lettered");
		} catch (Exception e) {
			if (job.attemptCount() + 1 >= MAX_ATTEMPTS) {
				deadLetterService.deadLetter(job, e);
				syncMetrics.recordProcessed("dead_lettered");
			} else {
				SyncJob retryJob = new SyncJob(job.id(), job.surveyId(), job.kind(), job.cursor(), job.responseId(),
						job.triggerType(), job.attemptCount() + 1, Instant.now());
				Duration delay = Duration.ofMillis((long) (500 * Math.pow(2, job.attemptCount())));
				jobQueue.enqueue(retryJob, delay);
				syncMetrics.recordProcessed("retried");
			}
		}
	}
}
