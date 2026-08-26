package com.testingai.surveyimporter.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Component
public class SurveyMonkeyClient {

	private static final Logger log = LoggerFactory.getLogger(SurveyMonkeyClient.class);
	private static final String CIRCUIT_BREAKER_NAME = "surveyMonkey";

	private final RestClient restClient;
	private final CircuitBreakerRegistry circuitBreakerRegistry;

	public SurveyMonkeyClient(RestClient.Builder builder, @Value("${surveymonkey.base-url}") String baseUrl,
			CircuitBreakerRegistry circuitBreakerRegistry) {
		this.restClient = builder.baseUrl(baseUrl).build();
		this.circuitBreakerRegistry = circuitBreakerRegistry;
	}

	@PostConstruct
	public void logCircuitBreakerStateTransitions() {
		circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME).getEventPublisher()
				.onStateTransition(event -> log.warn("Circuit breaker '{}' transitioned {} -> {}", CIRCUIT_BREAKER_NAME,
						event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
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
