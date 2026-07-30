package com.testingai.webhooks.producer.delivery;

import java.time.Duration;
import java.util.List;

/**
 * The Nth entry is the delay before retry attempt N+1. {@code delays.size()} is the maximum number of attempts before a
 * delivery is dead-lettered. Externalized (rather than a hardcoded constant in {@link WebhookDispatcher}) so tests can
 * use a millisecond-scale schedule instead of waiting through the real 1s/2s/4s/8s/16s production backoff.
 */
public record RetryBackoffSchedule(List<Duration> delays) {

	public int maxAttempts() {
		return delays.size();
	}

	public Duration delayForAttempt(int attemptNumber) {
		return delays.get(attemptNumber - 1);
	}
}
