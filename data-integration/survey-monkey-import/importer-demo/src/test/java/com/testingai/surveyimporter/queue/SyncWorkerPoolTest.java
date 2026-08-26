package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.client.RetryableSyncException;
import com.testingai.surveyimporter.connector.ConnectorService;
import com.testingai.surveyimporter.dlq.DeadLetterService;
import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.monitoring.SyncMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SyncWorkerPoolTest {

	@Mock
	private JobQueue jobQueue;

	@Mock
	private ConnectorService connectorService;

	@Mock
	private DeadLetterService deadLetterService;

	@Mock
	private SyncMetrics syncMetrics;

	private SyncWorkerPool workerPool;

	@BeforeEach
	void setUp() {
		workerPool = new SyncWorkerPool(jobQueue, connectorService, deadLetterService, syncMetrics);
	}

	@Test
	void successfulProcessingRecordsSuccessMetric() {
		SyncJob job = newJob(0);

		workerPool.process(job);

		verify(connectorService).process(job);
		verify(syncMetrics).recordProcessed("success");
		verifyNoInteractions(deadLetterService);
	}

	@Test
	void retryableFailureRequeuesWithIncrementedAttemptCount() {
		SyncJob job = newJob(0);
		doThrow(new RetryableSyncException("boom", null)).when(connectorService).process(job);

		workerPool.process(job);

		ArgumentCaptor<SyncJob> captor = ArgumentCaptor.forClass(SyncJob.class);
		verify(jobQueue).enqueue(captor.capture(), any(Duration.class));
		assertThat(captor.getValue().attemptCount()).isEqualTo(1);
		verify(syncMetrics).recordProcessed("retried");
		verifyNoInteractions(deadLetterService);
	}

	@Test
	void exhaustedAttemptsDeadLetters() {
		SyncJob job = newJob(4);
		doThrow(new RetryableSyncException("boom", null)).when(connectorService).process(job);

		workerPool.process(job);

		verify(deadLetterService).deadLetter(eq(job), any());
		verify(syncMetrics).recordProcessed("dead_lettered");
		verify(jobQueue, never()).enqueue(any(), any());
	}

	@Test
	void permanentFailureDeadLettersImmediatelyWithoutRequeue() {
		SyncJob job = newJob(0);
		doThrow(new PermanentSyncException("bad data")).when(connectorService).process(job);

		workerPool.process(job);

		verify(deadLetterService).deadLetter(eq(job), any());
		verify(syncMetrics).recordProcessed("dead_lettered");
		verify(jobQueue, never()).enqueue(any(), any());
	}

	private SyncJob newJob(int attemptCount) {
		return new SyncJob(UUID.randomUUID(), "survey-1", JobKind.PAGE_SYNC, null, null, TriggerType.MANUAL,
				attemptCount, Instant.now());
	}
}
