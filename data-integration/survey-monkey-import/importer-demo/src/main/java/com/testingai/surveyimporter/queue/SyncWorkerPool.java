package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.connector.ConnectorService;
import com.testingai.surveyimporter.dlq.DeadLetterService;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.monitoring.SyncMetrics;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SyncWorkerPool {

	private static final Logger log = LoggerFactory.getLogger(SyncWorkerPool.class);
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
		log.info("Processing job survey={} kind={} cursor={} responseId={} trigger={} attempt={}", job.surveyId(),
				job.kind(), job.cursor(), job.responseId(), job.triggerType(), job.attemptCount());
		try {
			connectorService.process(job);
			log.info("Job succeeded survey={} kind={} cursor={}", job.surveyId(), job.kind(), job.cursor());
			syncMetrics.recordProcessed("success");
		} catch (PermanentSyncException e) {
			log.warn("Job permanently failed survey={} kind={} cursor={}: {} — dead-lettering", job.surveyId(),
					job.kind(), job.cursor(), e.getMessage());
			deadLetterService.deadLetter(job, e);
			syncMetrics.recordProcessed("dead_lettered");
		} catch (Exception e) {
			if (job.attemptCount() + 1 >= MAX_ATTEMPTS) {
				log.warn("Job survey={} kind={} cursor={} exhausted {} attempts ({}) — dead-lettering", job.surveyId(),
						job.kind(), job.cursor(), MAX_ATTEMPTS, e.getMessage());
				deadLetterService.deadLetter(job, e);
				syncMetrics.recordProcessed("dead_lettered");
			} else {
				SyncJob retryJob = new SyncJob(job.id(), job.surveyId(), job.kind(), job.cursor(), job.responseId(),
						job.triggerType(), job.attemptCount() + 1, Instant.now());
				Duration delay = Duration.ofMillis((long) (500 * Math.pow(2, job.attemptCount())));
				log.info("Job survey={} kind={} cursor={} failed ({}) — retrying in {}ms (attempt {}/{})",
						job.surveyId(), job.kind(), job.cursor(), e.getMessage(), delay.toMillis(),
						retryJob.attemptCount(), MAX_ATTEMPTS);
				jobQueue.enqueue(retryJob, delay);
				syncMetrics.recordProcessed("retried");
			}
		}
	}
}
