package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobQueueTest {

	private final JobQueue jobQueue = new JobQueue();

	@Test
	void enqueueWithoutDelayIsImmediatelyAvailable() throws InterruptedException {
		SyncJob job = newJob();
		jobQueue.enqueue(job);

		assertThat(jobQueue.take()).isEqualTo(job);
	}

	@Test
	void enqueueWithDelayIsCountedInSizeBeforeItsDelayElapses() throws InterruptedException {
		SyncJob job = newJob();
		jobQueue.enqueue(job, Duration.ofMillis(200));

		assertThat(jobQueue.size()).isEqualTo(1);

		assertThat(jobQueue.take()).isEqualTo(job);
	}

	private SyncJob newJob() {
		return new SyncJob(UUID.randomUUID(), "survey-1", JobKind.PAGE_SYNC, null, null, TriggerType.MANUAL, 0,
				Instant.now());
	}
}
