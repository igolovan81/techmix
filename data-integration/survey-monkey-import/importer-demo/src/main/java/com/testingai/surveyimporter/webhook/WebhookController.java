package com.testingai.surveyimporter.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.queue.JobQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
public class WebhookController {

	private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

	private final WebhookSignatureVerifier signatureVerifier;
	private final JobQueue jobQueue;
	private final ObjectMapper objectMapper;

	public WebhookController(WebhookSignatureVerifier signatureVerifier, JobQueue jobQueue, ObjectMapper objectMapper) {
		this.signatureVerifier = signatureVerifier;
		this.jobQueue = jobQueue;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/webhooks/surveymonkey")
	public ResponseEntity<Void> receive(@RequestBody String rawBody,
			@RequestHeader("X-SurveyMonkey-Signature") String signatureHeader) {
		if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
			log.warn("Rejected webhook: invalid signature");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		WebhookEvent event = parse(rawBody);
		log.info("Accepted webhook for survey {} response {} (type={})", event.surveyId(), event.responseId(),
				event.eventType());
		jobQueue.enqueue(new SyncJob(UUID.randomUUID(), event.surveyId(), JobKind.SINGLE_RESPONSE_SYNC, null,
				event.responseId(), TriggerType.WEBHOOK, 0, Instant.now()));
		return ResponseEntity.ok().build();
	}

	private WebhookEvent parse(String rawBody) {
		try {
			return objectMapper.readValue(rawBody, WebhookEvent.class);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Invalid webhook payload", e);
		}
	}
}
