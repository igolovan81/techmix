package com.testingai.disruptor.errors;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;
import com.testingai.disruptor.errors.util.FailureSimulator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ErrorsService {

	private static final Logger log = LoggerFactory.getLogger(ErrorsService.class);
	private static final int RING_BUFFER_SIZE = 2048;

	private final AtomicLong succeeded = new AtomicLong();
	private final AtomicLong failed = new AtomicLong();
	private final AtomicReference<CountDownLatch> latchRef = new AtomicReference<>();

	private Disruptor<OrderEvent> disruptor;
	private RingBuffer<OrderEvent> ringBuffer;

	@PostConstruct
	public void start() {
		disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
				ProducerType.SINGLE, new BlockingWaitStrategy());
		disruptor.handleEventsWith(this::onEvent);
		disruptor.setDefaultExceptionHandler(new CountingExceptionHandler());
		ringBuffer = disruptor.start();
	}

	@PreDestroy
	public void shutdown() {
		disruptor.shutdown();
	}

	public ErrorsResult process(int eventCount) {
		succeeded.set(0);
		failed.set(0);
		latchRef.set(new CountDownLatch(eventCount));

		long start = System.currentTimeMillis();
		for (long i = 0; i < eventCount; i++) {
			ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
		}
		await(latchRef.get());
		long elapsed = System.currentTimeMillis() - start;

		return new ErrorsResult(succeeded.get(), failed.get(), elapsed);
	}

	private void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
		FailureSimulator.maybeThrow("order-processing");
		succeeded.incrementAndGet();
		CountDownLatch latch = latchRef.get();
		if (latch != null) {
			latch.countDown();
		}
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private class CountingExceptionHandler implements ExceptionHandler<OrderEvent> {

		@Override
		public void handleEventException(Throwable ex, long sequence, OrderEvent event) {
			log.warn("Handler failed processing sequence {}: {}", sequence, ex.getMessage());
			failed.incrementAndGet();
			CountDownLatch latch = latchRef.get();
			if (latch != null) {
				latch.countDown();
			}
		}

		@Override
		public void handleOnStartException(Throwable ex) {
			log.error("Disruptor failed to start", ex);
		}

		@Override
		public void handleOnShutdownException(Throwable ex) {
			log.error("Disruptor failed to shut down cleanly", ex);
		}
	}
}
