package com.testingai.surveysource.seed;

import com.testingai.surveysource.domain.Answer;
import com.testingai.surveysource.domain.SourceSurveyResponse;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeedDataService {

	private static final List<String> SURVEY_IDS = List.of("survey-1", "survey-2", "survey-3");
	private static final int RESPONSES_PER_SURVEY = 250;

	private final Map<String, List<SourceSurveyResponse>> responsesBySurvey = new ConcurrentHashMap<>();

	@PostConstruct
	public void seed() {
		Instant now = Instant.now();
		for (String surveyId : SURVEY_IDS) {
			List<SourceSurveyResponse> responses = new ArrayList<>();
			for (int i = 0; i < RESPONSES_PER_SURVEY; i++) {
				String responseId = surveyId + "-response-" + i;
				Instant dateModified = now.minus(Duration.ofHours(3L * (RESPONSES_PER_SURVEY - i)));
				List<Answer> answers = List.of(new Answer("q1", "answer-" + i));
				responses.add(new SourceSurveyResponse(responseId, surveyId, dateModified, answers));
			}
			responsesBySurvey.put(surveyId, responses);
		}
	}

	public List<String> surveyIds() {
		return SURVEY_IDS;
	}

	public List<SourceSurveyResponse> responsesFor(String surveyId, Instant startModifiedAt) {
		List<SourceSurveyResponse> all = responsesBySurvey.getOrDefault(surveyId, List.of());
		if (startModifiedAt == null) {
			return all;
		}
		return all.stream().filter(response -> response.dateModified().isAfter(startModifiedAt)).toList();
	}

	public Optional<SourceSurveyResponse> findResponse(String surveyId, String responseId) {
		return responsesBySurvey.getOrDefault(surveyId, List.of()).stream()
				.filter(response -> response.id().equals(responseId)).findFirst();
	}
}
