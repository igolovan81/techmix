package com.testingai.surveyimporter.scheduler;

import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.queue.JobQueue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SyncSchedulerTest {

	@Test
	void enqueuesOnePageSyncJobPerKnownSurvey() {
		JobQueue jobQueue = mock(JobQueue.class);
		SyncScheduler scheduler = new SyncScheduler(jobQueue, List.of("survey-1", "survey-2"));

		scheduler.scheduleSync();

		verify(jobQueue, times(2)).enqueue(argThat(job -> job.triggerType() == TriggerType.SCHEDULED));
	}
}
