package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;

public sealed interface StepOutcome permits StepOutcome.Success, StepOutcome.Failure {

	record Success(SagaStep step) implements StepOutcome {
	}

	record Failure(SagaStep step, String reason) implements StepOutcome {
	}
}
