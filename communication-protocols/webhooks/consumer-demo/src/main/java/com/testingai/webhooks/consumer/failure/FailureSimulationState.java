package com.testingai.webhooks.consumer.failure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class FailureSimulationState {

	private final AtomicInteger remainingFailures = new AtomicInteger(0);

	public void arm(int count) {
		remainingFailures.set(count);
		log.info("armed to simulate {} upcoming failure(s)", count);
	}

	public boolean consumeFailure() {
		return remainingFailures.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0;
	}

	public int remaining() {
		return remainingFailures.get();
	}
}
