package com.testingai.surveysource.failure;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.domain.FailureMode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureInjectorTest {

	private final FailureInjector failureInjector = new FailureInjector();

	@Test
	void defaultsToNoInjection() {
		assertThat(failureInjector.shouldInject(FailureMode.RATE_LIMIT)).isFalse();
		assertThat(failureInjector.current()).isEqualTo(new FailureConfig(FailureMode.NONE, 0.0));
	}

	@Test
	void injectsOnlyForTheConfiguredModeAtTheConfiguredRate() {
		failureInjector.configure(new FailureConfig(FailureMode.SERVER_ERROR, 1.0));

		assertThat(failureInjector.shouldInject(FailureMode.SERVER_ERROR)).isTrue();
		assertThat(failureInjector.shouldInject(FailureMode.RATE_LIMIT)).isFalse();
		assertThat(failureInjector.current()).isEqualTo(new FailureConfig(FailureMode.SERVER_ERROR, 1.0));
	}
}
