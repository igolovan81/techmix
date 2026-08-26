package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.client.RetryableSyncException;
import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.entity.DeadLetterJobEntity;
import com.testingai.surveyimporter.queue.JobQueue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({DeadLetterService.class, JobQueue.class})
class DeadLetterServiceTest {

	@Autowired
	private DeadLetterService deadLetterService;

	@Autowired
	private DeadLetterJobRepository repository;

	@Autowired
	private JobQueue jobQueue;

	@Test
	void deadLetterPersistsFullContext() {
		SyncJob job = new SyncJob(UUID.randomUUID(), "survey-1", JobKind.PAGE_SYNC, "3", null, TriggerType.SCHEDULED, 5,
				Instant.now());

		deadLetterService.deadLetter(job, new RetryableSyncException("boom", null));

		List<DeadLetterJobEntity> all = repository.findAll();
		assertThat(all).hasSize(1);
		assertThat(all.get(0).getSurveyId()).isEqualTo("survey-1");
		assertThat(all.get(0).getCursor()).isEqualTo("3");
		assertThat(all.get(0).getAttemptCount()).isEqualTo(5);
		assertThat(all.get(0).getErrorClass()).isEqualTo(RetryableSyncException.class.getName());
	}

	@Test
	void redriveReenqueuesWithResetAttemptCountAndRemovesTheEntry() throws InterruptedException {
		SyncJob job = new SyncJob(UUID.randomUUID(), "survey-2", JobKind.SINGLE_RESPONSE_SYNC, null, "resp-9",
				TriggerType.WEBHOOK, 5, Instant.now());
		deadLetterService.deadLetter(job, new PermanentSyncException("bad data"));
		Long id = repository.findAll().get(0).getId();

		deadLetterService.redrive(id);

		assertThat(repository.findById(id)).isEmpty();
		SyncJob redriven = jobQueue.take();
		assertThat(redriven.surveyId()).isEqualTo("survey-2");
		assertThat(redriven.responseId()).isEqualTo("resp-9");
		assertThat(redriven.attemptCount()).isZero();
	}
}
