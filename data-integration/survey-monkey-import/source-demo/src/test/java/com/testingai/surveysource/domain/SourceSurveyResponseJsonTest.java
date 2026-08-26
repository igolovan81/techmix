package com.testingai.surveysource.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceSurveyResponseJsonTest {

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
			.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

	@Test
	void serializesDateModifiedAsSnakeCase() throws Exception {
		SourceSurveyResponse response = new SourceSurveyResponse("resp-1", "survey-1",
				Instant.parse("2026-01-01T00:00:00Z"), List.of(new Answer("q1", "yes")));

		String json = objectMapper.writeValueAsString(response);

		assertThat(json).contains("\"date_modified\"").contains("\"survey_id\"").contains("\"question_id\"");
	}
}
