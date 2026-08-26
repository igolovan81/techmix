package com.testingai.surveyimporter.client;

import java.util.List;

public record ResponsesPage(List<SourceSurveyResponseView> data, int page, int perPage, int total, LinksView links) {
}
