package com.testingai.surveysource.domain;

import java.util.List;

public record ResponsesPage(List<SourceSurveyResponse> data, int page, int perPage, int total, Links links) {
}
