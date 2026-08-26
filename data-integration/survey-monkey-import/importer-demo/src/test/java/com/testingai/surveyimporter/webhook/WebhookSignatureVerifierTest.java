package com.testingai.surveyimporter.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

	private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("test-secret");

	@Test
	void validSignatureMatchesComputedHmac() {
		String body = "{\"survey_id\":\"survey-1\"}";
		String expected = "sha256=" + WebhookSignatureVerifier.hmacSha256Hex(body, "test-secret");

		assertThat(verifier.isValid(body, expected)).isTrue();
	}

	@Test
	void tamperedBodyFailsVerification() {
		String signature = "sha256=" + WebhookSignatureVerifier.hmacSha256Hex("original", "test-secret");

		assertThat(verifier.isValid("tampered", signature)).isFalse();
	}
}
