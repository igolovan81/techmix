package com.testingai.webhooks.consumer.failure;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FailureSimulationState {

	private final AtomicInteger remainingFailures = new AtomicInteger(0);

	public void arm(int count) {
		remainingFailures.set(count);
	}

	public boolean consumeFailure() {
		return remainingFailures.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0;
	}

	public int remaining() {
		return remainingFailures.get();
	}
}
