package com.testingai.surveysource.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDispatcherTest {

	@Test
	void hmacSha256HexIsDeterministicAndHexEncoded() {
		String hex1 = WebhookDispatcher.hmacSha256Hex("payload", "secret");
		String hex2 = WebhookDispatcher.hmacSha256Hex("payload", "secret");

		assertThat(hex1).isEqualTo(hex2).hasSize(64).matches("[0-9a-f]+");
	}

	@Test
	void differentPayloadsProduceDifferentSignatures() {
		String hexA = WebhookDispatcher.hmacSha256Hex("payload-a", "secret");
		String hexB = WebhookDispatcher.hmacSha256Hex("payload-b", "secret");

		assertThat(hexA).isNotEqualTo(hexB);
	}
}
