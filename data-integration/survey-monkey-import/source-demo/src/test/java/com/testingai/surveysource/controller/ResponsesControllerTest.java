package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureMode;
import com.testingai.surveysource.domain.SourceSurveyResponse;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.seed.SeedDataService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResponsesController.class)
class ResponsesControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SeedDataService seedDataService;

	@MockitoBean
	private FailureInjector failureInjector;

	@Test
	void bulkReturnsPagedResponsesWithNextLink() throws Exception {
		List<SourceSurveyResponse> responses = IntStream.range(0, 30)
				.mapToObj(i -> new SourceSurveyResponse("resp-" + i, "survey-1", Instant.now(), List.of())).toList();
		when(seedDataService.responsesFor(eq("survey-1"), isNull())).thenReturn(responses);
		when(failureInjector.shouldInject(any())).thenReturn(false);

		mockMvc.perform(get("/v3/surveys/survey-1/responses/bulk").param("page", "1").param("per_page", "25"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(25))
				.andExpect(jsonPath("$.links.next").value("2"));
	}

	@Test
	void lastPageHasNoNextLink() throws Exception {
		List<SourceSurveyResponse> responses = IntStream.range(0, 30)
				.mapToObj(i -> new SourceSurveyResponse("resp-" + i, "survey-1", Instant.now(), List.of())).toList();
		when(seedDataService.responsesFor(eq("survey-1"), isNull())).thenReturn(responses);
		when(failureInjector.shouldInject(any())).thenReturn(false);

		mockMvc.perform(get("/v3/surveys/survey-1/responses/bulk").param("page", "2").param("per_page", "25"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(5))
				.andExpect(jsonPath("$.links.next").doesNotExist());
	}

	@Test
	void rateLimitFailureModeReturns429WithRetryAfterHeader() throws Exception {
		when(failureInjector.shouldInject(FailureMode.RATE_LIMIT)).thenReturn(true);

		mockMvc.perform(get("/v3/surveys/survey-1/responses/bulk")).andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "2"));
	}

	@Test
	void singleResponseReturnsNotFoundWhenAbsent() throws Exception {
		when(seedDataService.findResponse("survey-1", "missing")).thenReturn(Optional.empty());

		mockMvc.perform(get("/v3/surveys/survey-1/responses/missing")).andExpect(status().isNotFound());
	}
}
