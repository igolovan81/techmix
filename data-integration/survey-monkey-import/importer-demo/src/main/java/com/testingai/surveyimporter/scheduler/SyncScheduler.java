package com.testingai.surveyimporter.scheduler;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.queue.JobQueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class SyncScheduler {

	private final JobQueue jobQueue;
	private final List<String> knownSurveyIds;

	public SyncScheduler(JobQueue jobQueue,
			@Value("#{'${importer.known-survey-ids}'.split(',')}") List<String> knownSurveyIds) {
		this.jobQueue = jobQueue;
		this.knownSurveyIds = knownSurveyIds;
	}

	@Scheduled(fixedDelayString = "${importer.scheduler.fixed-delay-ms:60000}")
	public void scheduleSync() {
		for (String surveyId : knownSurveyIds) {
			jobQueue.enqueue(new SyncJob(UUID.randomUUID(), surveyId, JobKind.PAGE_SYNC, null, null,
					TriggerType.SCHEDULED, 0, Instant.now()));
		}
	}
}
