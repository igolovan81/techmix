package com.testingai.webhooks.consumer.failure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSimulationStateTest {

	private final FailureSimulationState state = new FailureSimulationState();

	@Test
	void consumeFailure_returnsFalse_whenNotArmed() {
		assertThat(state.consumeFailure()).isFalse();
	}

	@Test
	void consumeFailure_returnsTrueExactlyArmedCountTimes_thenFalse() {
		state.arm(3);

		assertThat(state.consumeFailure()).isTrue();
		assertThat(state.consumeFailure()).isTrue();
		assertThat(state.consumeFailure()).isTrue();
		assertThat(state.consumeFailure()).isFalse();
	}

	@Test
	void remaining_reflectsCountdown() {
		state.arm(2);
		assertThat(state.remaining()).isEqualTo(2);

		state.consumeFailure();

		assertThat(state.remaining()).isEqualTo(1);
	}
}
