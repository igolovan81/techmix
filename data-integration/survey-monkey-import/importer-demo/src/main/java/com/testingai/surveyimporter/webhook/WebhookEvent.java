package com.testingai.surveyimporter.webhook;

public record WebhookEvent(String surveyId, String responseId, String eventType) {
}
