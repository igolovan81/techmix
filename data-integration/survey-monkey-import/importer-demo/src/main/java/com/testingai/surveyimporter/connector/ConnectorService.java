package com.testingai.surveyimporter.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.client.ResponsesPage;
import com.testingai.surveyimporter.client.SourceSurveyResponseView;
import com.testingai.surveyimporter.client.SurveyMonkeyClient;
import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.entity.SyncWatermarkEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SurveyResponseUpsert;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;
import com.testingai.surveyimporter.storage.UpsertService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ConnectorService {

	private static final Logger log = LoggerFactory.getLogger(ConnectorService.class);
	private static final int PAGE_SIZE = 25;

	private final SurveyMonkeyClient client;
	private final UpsertService upsertService;
	private final SyncWatermarkRepository watermarkRepository;
	private final JobQueue jobQueue;
	private final ObjectMapper objectMapper;

	public ConnectorService(SurveyMonkeyClient client, UpsertService upsertService,
			SyncWatermarkRepository watermarkRepository, JobQueue jobQueue, ObjectMapper objectMapper) {
		this.client = client;
		this.upsertService = upsertService;
		this.watermarkRepository = watermarkRepository;
		this.jobQueue = jobQueue;
		this.objectMapper = objectMapper;
	}

	public void process(SyncJob job) {
		if (job.kind() == JobKind.SINGLE_RESPONSE_SYNC) {
			processSingleResponse(job);
		} else {
			processPage(job);
		}
	}

	private void processPage(SyncJob job) {
		Instant startModifiedAt = job.cursor() == null
				? watermarkRepository.findById(job.surveyId()).map(SyncWatermarkEntity::getLastSyncedAt).orElse(null)
				: null;
		log.info("Fetching survey {} page (cursor={}, startModifiedAt={})", job.surveyId(), job.cursor(),
				startModifiedAt);
		ResponsesPage page = client.fetchResponsesPage(job.surveyId(), job.cursor(), PAGE_SIZE, startModifiedAt);

		for (SourceSurveyResponseView response : page.data()) {
			if (response.id() == null || response.dateModified() == null) {
				throw new PermanentSyncException("Malformed response in page: missing id or dateModified");
			}
		}
		for (SourceSurveyResponseView response : page.data()) {
			upsertService.upsert(new SurveyResponseUpsert(job.surveyId(), response.id(), response.dateModified(),
					toPayload(response)));
		}

		boolean hasNext = page.links() != null && page.links().next() != null;
		log.info("Fetched survey {} page (cursor={}): {} responses, hasNext={}", job.surveyId(), job.cursor(),
				page.data().size(), hasNext);
		if (hasNext) {
			jobQueue.enqueue(new SyncJob(UUID.randomUUID(), job.surveyId(), JobKind.PAGE_SYNC, page.links().next(),
					null, job.triggerType(), 0, Instant.now()));
		} else {
			log.info("Survey {} sync pass complete — updating watermark", job.surveyId());
			watermarkRepository.save(new SyncWatermarkEntity(job.surveyId(), Instant.now()));
		}
	}

	private void processSingleResponse(SyncJob job) {
		log.info("Fetching single response {} for survey {}", job.responseId(), job.surveyId());
		SourceSurveyResponseView response = client.fetchSingleResponse(job.surveyId(), job.responseId());
		if (response.id() == null || response.dateModified() == null) {
			throw new PermanentSyncException("Malformed single response");
		}
		upsertService.upsert(
				new SurveyResponseUpsert(job.surveyId(), response.id(), response.dateModified(), toPayload(response)));
	}

	private String toPayload(SourceSurveyResponseView response) {
		try {
			return objectMapper.writeValueAsString(response.answers());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize response answers", e);
		}
	}
}
