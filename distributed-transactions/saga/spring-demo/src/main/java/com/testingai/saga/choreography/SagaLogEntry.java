package com.testingai.saga.choreography;

import java.time.Instant;

public record SagaLogEntry(String step, Outcome outcome, String detail, Instant timestamp) {

	public enum Outcome {
		SUCCEEDED,
		FAILED,
		COMPENSATED
	}
}
