package com.testingai.surveysource.seed;

import com.testingai.surveysource.domain.SourceSurveyResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataServiceTest {

	private final SeedDataService seedDataService = new SeedDataService();

	@BeforeEach
	void setUp() {
		seedDataService.seed();
	}

	@Test
	void seedsExpectedSurveysAndResponseCounts() {
		assertThat(seedDataService.surveyIds()).containsExactly("survey-1", "survey-2", "survey-3");
		assertThat(seedDataService.responsesFor("survey-1", null)).hasSize(250);
	}

	@Test
	void filtersByStartModifiedAt() {
		List<SourceSurveyResponse> all = seedDataService.responsesFor("survey-1", null);
		Instant midpoint = all.get(125).dateModified();

		List<SourceSurveyResponse> recent = seedDataService.responsesFor("survey-1", midpoint);

		assertThat(recent).allMatch(r -> r.dateModified().isAfter(midpoint));
		assertThat(recent.size()).isLessThan(all.size());
	}

	@Test
	void findResponseLocatesByIdWithinASurvey() {
		Optional<SourceSurveyResponse> found = seedDataService.findResponse("survey-1", "survey-1-response-0");

		assertThat(found).isPresent();
		assertThat(seedDataService.findResponse("survey-1", "does-not-exist")).isEmpty();
	}
}
