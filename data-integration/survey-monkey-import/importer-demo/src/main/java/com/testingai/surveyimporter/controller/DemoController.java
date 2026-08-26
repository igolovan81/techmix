package com.testingai.surveyimporter.controller;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SurveyResponseRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demo")
public class DemoController {

	private final JobQueue jobQueue;
	private final SurveyResponseRepository repository;

	public DemoController(JobQueue jobQueue, SurveyResponseRepository repository) {
		this.jobQueue = jobQueue;
		this.repository = repository;
	}

	@PostMapping("/surveys/{surveyId}/sync")
	public ResponseEntity<Void> triggerSync(@PathVariable String surveyId) {
		jobQueue.enqueue(new SyncJob(UUID.randomUUID(), surveyId, JobKind.PAGE_SYNC, null, null, TriggerType.MANUAL, 0,
				Instant.now()));
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/surveys/{surveyId}/responses")
	public List<SurveyResponseView> responses(@PathVariable String surveyId) {
		return repository.findBySurveyId(surveyId).stream().map(
				entity -> new SurveyResponseView(entity.getResponseId(), entity.getDateModified(), entity.getPayload()))
				.toList();
	}
}
