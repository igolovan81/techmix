package com.testingai.webhooks.producer.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {

	private final HmacSigner hmacSigner = new HmacSigner();

	@Test
	void sign_producesStableHexDigest_forKnownSecretAndPayload() {
		String signature = hmacSigner.sign("test-secret", "{\"hello\":\"world\"}");

		assertThat(signature).isEqualTo("84cc33df716ed0b0598f07437c94069ace3730358778a592bd6bbd1423d111f3");
	}

	@Test
	void sign_producesDifferentDigests_forDifferentSecrets() {
		String signatureA = hmacSigner.sign("secret-a", "same payload");
		String signatureB = hmacSigner.sign("secret-b", "same payload");

		assertThat(signatureA).isNotEqualTo(signatureB);
	}

	@Test
	void sign_producesDifferentDigests_forDifferentPayloads() {
		String signatureA = hmacSigner.sign("same-secret", "payload a");
		String signatureB = hmacSigner.sign("same-secret", "payload b");

		assertThat(signatureA).isNotEqualTo(signatureB);
	}
}
