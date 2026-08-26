package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.domain.FailureMode;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.webhook.WebhookDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FailureInjector failureInjector;

	@MockitoBean
	private WebhookDispatcher webhookDispatcher;

	@Test
	void setFailureModeUpdatesInjector() throws Exception {
		mockMvc.perform(post("/admin/failure-mode").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\":\"RATE_LIMIT\",\"rate\":0.5}")).andExpect(status().isOk());

		verify(failureInjector).configure(new FailureConfig(FailureMode.RATE_LIMIT, 0.5));
	}

	@Test
	void triggerWebhookDelegatesToDispatcher() throws Exception {
		mockMvc.perform(post("/admin/webhooks/trigger").param("surveyId", "survey-1").param("responseId", "resp-1"))
				.andExpect(status().isOk());

		verify(webhookDispatcher).dispatch("survey-1", "resp-1");
	}
}
