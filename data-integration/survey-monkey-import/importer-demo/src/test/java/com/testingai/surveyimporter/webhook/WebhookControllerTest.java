package com.testingai.surveyimporter.webhook;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.queue.JobQueue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WebhookSignatureVerifier signatureVerifier;

	@MockitoBean
	private JobQueue jobQueue;

	@Test
	void validSignatureEnqueuesJobAndReturnsOk() throws Exception {
		String body = "{\"survey_id\":\"survey-1\",\"response_id\":\"resp-1\",\"event_type\":\"response_completed\"}";
		when(signatureVerifier.isValid(eq(body), anyString())).thenReturn(true);

		mockMvc.perform(post("/webhooks/surveymonkey").header("X-SurveyMonkey-Signature", "sha256=abc")
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

		verify(jobQueue).enqueue(argThat(job -> job.surveyId().equals("survey-1") && job.responseId().equals("resp-1")
				&& job.kind() == JobKind.SINGLE_RESPONSE_SYNC));
	}

	@Test
	void invalidSignatureIsRejected() throws Exception {
		when(signatureVerifier.isValid(anyString(), anyString())).thenReturn(false);

		mockMvc.perform(post("/webhooks/surveymonkey").header("X-SurveyMonkey-Signature", "sha256=bad")
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

		verifyNoInteractions(jobQueue);
	}
}
