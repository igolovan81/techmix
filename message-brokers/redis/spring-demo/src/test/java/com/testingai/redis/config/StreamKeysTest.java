package com.testingai.redis.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StreamKeysTest {

	@Test
	void allConstantsAreNonNullAndNonBlank() {
		assertThat(StreamKeys.SIMPLE).isNotBlank();
		assertThat(StreamKeys.WORK).isNotBlank();
		assertThat(StreamKeys.FANOUT).isNotBlank();
		assertThat(StreamKeys.PENDING).isNotBlank();
		assertThat(StreamKeys.TRIMMED).isNotBlank();
		assertThat(StreamKeys.PUBSUB_CHANNEL).isNotBlank();
	}

	@Test
	void trimMaxLenIsPositive() {
		assertThat(StreamKeys.TRIM_MAX_LEN).isGreaterThan(0);
	}
}
