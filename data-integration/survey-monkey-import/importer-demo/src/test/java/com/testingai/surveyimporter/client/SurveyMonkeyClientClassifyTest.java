package com.testingai.surveyimporter.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;

class SurveyMonkeyClientClassifyTest {

	@Test
	void tooManyRequestsIsRetryable() {
		RestClientResponseException e = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
				"Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);

		assertThat(SurveyMonkeyClient.classify(e)).isInstanceOf(RetryableSyncException.class);
	}

	@Test
	void serverErrorIsRetryable() {
		RestClientResponseException e = HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
				"Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);

		assertThat(SurveyMonkeyClient.classify(e)).isInstanceOf(RetryableSyncException.class);
	}

	@Test
	void notFoundIsPermanent() {
		RestClientResponseException e = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
				HttpHeaders.EMPTY, new byte[0], null);

		assertThat(SurveyMonkeyClient.classify(e)).isInstanceOf(PermanentSyncException.class);
	}
}
