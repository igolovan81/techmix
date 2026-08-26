package com.testingai.surveyimporter.controller;

import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.entity.SurveyResponseEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SurveyResponseRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JobQueue jobQueue;

	@MockitoBean
	private SurveyResponseRepository repository;

	@Test
	void triggerSyncEnqueuesManualJob() throws Exception {
		mockMvc.perform(post("/demo/surveys/survey-1/sync")).andExpect(status().isAccepted());

		verify(jobQueue)
				.enqueue(argThat(job -> job.surveyId().equals("survey-1") && job.triggerType() == TriggerType.MANUAL));
	}

	@Test
	void listsImportedResponsesForASurvey() throws Exception {
		SurveyResponseEntity entity = new SurveyResponseEntity();
		entity.setResponseId("resp-1");
		entity.setDateModified(Instant.now());
		entity.setPayload("[]");
		when(repository.findBySurveyId("survey-1")).thenReturn(List.of(entity));

		mockMvc.perform(get("/demo/surveys/survey-1/responses")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].response_id").value("resp-1"));
	}
}
