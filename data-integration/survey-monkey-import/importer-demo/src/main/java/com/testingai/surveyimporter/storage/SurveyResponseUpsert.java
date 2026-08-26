package com.testingai.surveyimporter.storage;

import java.time.Instant;

public record SurveyResponseUpsert(String surveyId, String responseId, Instant dateModified, String payload) {
}
