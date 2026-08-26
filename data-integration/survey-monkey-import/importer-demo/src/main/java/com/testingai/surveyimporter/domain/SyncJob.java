package com.testingai.surveyimporter.domain;

import java.time.Instant;
import java.util.UUID;

public record SyncJob(UUID id, String surveyId, JobKind kind, String cursor, String responseId, TriggerType triggerType,
		int attemptCount, Instant nextAttemptAt) {
}
