package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.entity.DeadLetterJobEntity;
import com.testingai.surveyimporter.queue.JobQueue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeadLetterService {

	private final DeadLetterJobRepository repository;
	private final JobQueue jobQueue;

	public DeadLetterService(DeadLetterJobRepository repository, JobQueue jobQueue) {
		this.repository = repository;
		this.jobQueue = jobQueue;
	}

	@Transactional
	public void deadLetter(SyncJob job, Exception cause) {
		DeadLetterJobEntity entity = new DeadLetterJobEntity();
		entity.setSurveyId(job.surveyId());
		entity.setKind(job.kind().name());
		entity.setCursor(job.cursor());
		entity.setResponseId(job.responseId());
		entity.setTriggerType(job.triggerType().name());
		entity.setAttemptCount(job.attemptCount());
		entity.setErrorClass(cause.getClass().getName());
		entity.setErrorMessage(cause.getMessage());
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setLastAttemptAt(now);
		repository.save(entity);
	}

	public List<DeadLetterJobEntity> list() {
		return repository.findAll();
	}

	@Transactional
	public void redrive(Long id) {
		DeadLetterJobEntity entity = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Unknown DLQ entry: " + id));
		SyncJob job = new SyncJob(UUID.randomUUID(), entity.getSurveyId(), JobKind.valueOf(entity.getKind()),
				entity.getCursor(), entity.getResponseId(), TriggerType.valueOf(entity.getTriggerType()), 0,
				Instant.now());
		jobQueue.enqueue(job);
		repository.delete(entity);
	}
}
