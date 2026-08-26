package com.testingai.surveyimporter.client;

import java.time.Instant;
import java.util.List;

public record SourceSurveyResponseView(String id, String surveyId, Instant dateModified, List<AnswerView> answers) {
}
