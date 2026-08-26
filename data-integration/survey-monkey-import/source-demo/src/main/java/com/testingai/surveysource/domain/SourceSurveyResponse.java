package com.testingai.surveysource.domain;

import java.time.Instant;
import java.util.List;

public record SourceSurveyResponse(String id, String surveyId, Instant dateModified, List<Answer> answers) {
}
