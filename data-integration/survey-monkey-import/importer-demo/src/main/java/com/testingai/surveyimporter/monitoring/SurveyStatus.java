package com.testingai.surveyimporter.monitoring;

import java.time.Instant;

public record SurveyStatus(String surveyId, Instant lastSyncedAt, Long lagSeconds) {
}
