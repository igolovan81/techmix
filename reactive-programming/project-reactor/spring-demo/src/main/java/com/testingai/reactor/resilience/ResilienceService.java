package com.testingai.reactor.resilience;

import org.springframework.stereotype.Service;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ResilienceService {

	private static final int FAST_PRODUCER_COUNT = 200;
	private static final int BUFFER_CAPACITY = 16;
	private static final Duration SLOW_CONSUMER_DELAY = Duration.ofMillis(5);
	private static final int RETRY_ATTEMPTS = 3;
	private static final Duration RETRY_BACKOFF = Duration.ofMillis(50);
	private static final Duration SLOW_CALL_DURATION = Duration.ofMillis(300);
	private static final Duration CALL_TIMEOUT = Duration.ofMillis(100);

	public Mono<BackpressureResultDto> demonstrateBackpressure(String strategy) {
		AtomicLong processed = new AtomicLong();
		AtomicLong droppedOrBuffered = new AtomicLong();

		Flux<Integer> fastProducer = Flux.range(0, FAST_PRODUCER_COUNT);

		Flux<Integer> withStrategy = "drop".equals(strategy)
				? fastProducer.onBackpressureDrop(dropped -> droppedOrBuffered.incrementAndGet())
				: fastProducer.onBackpressureBuffer(BUFFER_CAPACITY, buffered -> droppedOrBuffered.incrementAndGet(),
						BufferOverflowStrategy.DROP_OLDEST);

		return withStrategy.limitRate(1).delayElements(SLOW_CONSUMER_DELAY)
				.doOnNext(item -> processed.incrementAndGet())
				.then(Mono.fromSupplier(() -> new BackpressureResultDto(strategy, FAST_PRODUCER_COUNT, processed.get(),
						droppedOrBuffered.get())));
	}

	public Flux<String> retryDemo() {
		return Mono.<String>fromRunnable(() -> FailureSimulator.maybeThrow("retryDemo")).thenReturn("success")
				.retryWhen(Retry.backoff(RETRY_ATTEMPTS, RETRY_BACKOFF))
				.onErrorReturn("fallback-after-retries-exhausted").flux();
	}

	public Mono<String> timeoutDemo() {
		return Mono.delay(SLOW_CALL_DURATION).thenReturn("slow-response").timeout(CALL_TIMEOUT)
				.onErrorResume(TimeoutException.class, e -> Mono.just("fallback-after-timeout"));
	}
}
