package com.testingai.surveyimporter.connector;

import com.testingai.surveyimporter.client.AnswerView;
import com.testingai.surveyimporter.client.LinksView;
import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.client.ResponsesPage;
import com.testingai.surveyimporter.client.SourceSurveyResponseView;
import com.testingai.surveyimporter.client.SurveyMonkeyClient;
import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.entity.SyncWatermarkEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;
import com.testingai.surveyimporter.storage.UpsertService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

	@Mock
	private SurveyMonkeyClient client;

	@Mock
	private UpsertService upsertService;

	@Mock
	private SyncWatermarkRepository watermarkRepository;

	@Mock
	private JobQueue jobQueue;

	private ConnectorService connectorService;

	@BeforeEach
	void setUp() {
		connectorService = new ConnectorService(client, upsertService, watermarkRepository, jobQueue,
				new ObjectMapper());
	}

	@Test
	void lastPageUpdatesWatermarkWithoutEnqueueingContinuation() {
		SourceSurveyResponseView response = new SourceSurveyResponseView("resp-1", "survey-1", Instant.now(),
				List.of(new AnswerView("q1", "yes")));
		ResponsesPage page = new ResponsesPage(List.of(response), 1, 25, 1, new LinksView(null));
		when(client.fetchResponsesPage(eq("survey-1"), isNull(), anyInt(), any())).thenReturn(page);
		when(watermarkRepository.findById("survey-1")).thenReturn(Optional.empty());

		connectorService.process(newPageSyncJob(null));

		verify(upsertService).upsert(argThat(u -> u.responseId().equals("resp-1")));
		verify(watermarkRepository).save(any(SyncWatermarkEntity.class));
		verify(jobQueue, never()).enqueue(any());
	}

	@Test
	void pageWithNextCursorEnqueuesContinuationJob() {
		SourceSurveyResponseView response = new SourceSurveyResponseView("resp-1", "survey-1", Instant.now(),
				List.of());
		ResponsesPage page = new ResponsesPage(List.of(response), 1, 25, 50, new LinksView("2"));
		when(client.fetchResponsesPage(eq("survey-1"), isNull(), anyInt(), any())).thenReturn(page);
		when(watermarkRepository.findById("survey-1")).thenReturn(Optional.empty());

		connectorService.process(newPageSyncJob(null));

		ArgumentCaptor<SyncJob> captor = ArgumentCaptor.forClass(SyncJob.class);
		verify(jobQueue).enqueue(captor.capture());
		assertThat(captor.getValue().cursor()).isEqualTo("2");
		verify(watermarkRepository, never()).save(any());
	}

	@Test
	void pageWithMissingIdThrowsPermanentSyncException() {
		SourceSurveyResponseView malformed = new SourceSurveyResponseView(null, "survey-1", Instant.now(), List.of());
		ResponsesPage page = new ResponsesPage(List.of(malformed), 1, 25, 1, new LinksView(null));
		when(client.fetchResponsesPage(eq("survey-1"), isNull(), anyInt(), any())).thenReturn(page);

		assertThatThrownBy(() -> connectorService.process(newPageSyncJob(null)))
				.isInstanceOf(PermanentSyncException.class);
		verifyNoInteractions(upsertService);
	}

	@Test
	void singleResponseSyncUpsertsOneResponseAndSkipsWatermark() {
		SourceSurveyResponseView response = new SourceSurveyResponseView("resp-9", "survey-1", Instant.now(),
				List.of());
		when(client.fetchSingleResponse("survey-1", "resp-9")).thenReturn(response);

		SyncJob job = new SyncJob(UUID.randomUUID(), "survey-1", JobKind.SINGLE_RESPONSE_SYNC, null, "resp-9",
				TriggerType.WEBHOOK, 0, Instant.now());
		connectorService.process(job);

		verify(upsertService).upsert(argThat(u -> u.responseId().equals("resp-9")));
		verifyNoInteractions(watermarkRepository);
	}

	private SyncJob newPageSyncJob(String cursor) {
		return new SyncJob(UUID.randomUUID(), "survey-1", JobKind.PAGE_SYNC, cursor, null, TriggerType.MANUAL, 0,
				Instant.now());
	}
}
