package com.testingai.webhooks.consumer.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacVerifierTest {

	private final HmacVerifier hmacVerifier = new HmacVerifier();

	@Test
	void verify_returnsTrue_whenSignatureMatches() {
		boolean valid = hmacVerifier.verify("test-secret", "{\"hello\":\"world\"}",
				"sha256=84cc33df716ed0b0598f07437c94069ace3730358778a592bd6bbd1423d111f3");

		assertThat(valid).isTrue();
	}

	@Test
	void verify_returnsFalse_whenSignatureDoesNotMatch() {
		boolean valid = hmacVerifier.verify("test-secret", "{\"hello\":\"world\"}", "sha256=deadbeef");

		assertThat(valid).isFalse();
	}

	@Test
	void verify_returnsFalse_whenSecretDiffers() {
		boolean valid = hmacVerifier.verify("wrong-secret", "{\"hello\":\"world\"}",
				"sha256=84cc33df716ed0b0598f07437c94069ace3730358778a592bd6bbd1423d111f3");

		assertThat(valid).isFalse();
	}

	@Test
	void verify_returnsFalse_whenSignatureHeaderMissing() {
		assertThat(hmacVerifier.verify("test-secret", "{\"hello\":\"world\"}", null)).isFalse();
	}
}
