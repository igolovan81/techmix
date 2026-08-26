package com.testingai.surveysource.webhook;

public record WebhookEvent(String surveyId, String responseId, String eventType) {
}
