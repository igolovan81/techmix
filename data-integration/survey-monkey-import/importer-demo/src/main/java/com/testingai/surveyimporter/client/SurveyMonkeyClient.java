package com.testingai.surveyimporter.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Component
public class SurveyMonkeyClient {

	private final RestClient restClient;

	public SurveyMonkeyClient(RestClient.Builder builder, @Value("${surveymonkey.base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	@Retry(name = "surveyMonkey")
	@CircuitBreaker(name = "surveyMonkey")
	@RateLimiter(name = "surveyMonkey")
	public ResponsesPage fetchResponsesPage(String surveyId, String cursor, int perPage, Instant startModifiedAt) {
		try {
			return restClient.get().uri(uriBuilder -> {
				uriBuilder.path("/v3/surveys/{surveyId}/responses/bulk")
						.queryParam("page", cursor != null ? cursor : "1").queryParam("per_page", perPage);
				if (startModifiedAt != null) {
					uriBuilder.queryParam("start_modified_at", startModifiedAt.toString());
				}
				return uriBuilder.build(surveyId);
			}).retrieve().body(ResponsesPage.class);
		} catch (RestClientResponseException e) {
			throw classify(e);
		} catch (ResourceAccessException e) {
			throw new RetryableSyncException("Network error calling SurveyMonkey", e);
		}
	}

	@Retry(name = "surveyMonkey")
	@CircuitBreaker(name = "surveyMonkey")
	@RateLimiter(name = "surveyMonkey")
	public SourceSurveyResponseView fetchSingleResponse(String surveyId, String responseId) {
		try {
			return restClient.get().uri("/v3/surveys/{surveyId}/responses/{responseId}", surveyId, responseId)
					.retrieve().body(SourceSurveyResponseView.class);
		} catch (RestClientResponseException e) {
			throw classify(e);
		} catch (ResourceAccessException e) {
			throw new RetryableSyncException("Network error calling SurveyMonkey", e);
		}
	}

	static RuntimeException classify(RestClientResponseException e) {
		int status = e.getStatusCode().value();
		if (status == 429 || status >= 500) {
			return new RetryableSyncException("Retryable SurveyMonkey error: HTTP " + status, e);
		}
		return new PermanentSyncException("Permanent SurveyMonkey error: HTTP " + status, e);
	}
}
