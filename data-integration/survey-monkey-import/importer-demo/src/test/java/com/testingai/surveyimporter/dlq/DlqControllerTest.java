package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.entity.DeadLetterJobEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DlqController.class)
class DlqControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DeadLetterService deadLetterService;

	@Test
	void listReturnsDlqEntries() throws Exception {
		DeadLetterJobEntity entity = new DeadLetterJobEntity();
		entity.setId(1L);
		entity.setSurveyId("survey-1");
		entity.setCreatedAt(Instant.now());
		when(deadLetterService.list()).thenReturn(List.of(entity));

		mockMvc.perform(get("/demo/dlq")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].survey_id").value("survey-1"));
	}

	@Test
	void redriveDelegatesToService() throws Exception {
		mockMvc.perform(post("/demo/dlq/1/redrive")).andExpect(status().isOk());

		verify(deadLetterService).redrive(1L);
	}
}
