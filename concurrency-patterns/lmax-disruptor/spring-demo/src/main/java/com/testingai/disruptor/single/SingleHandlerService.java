package com.testingai.disruptor.single;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SingleHandlerService {

	private static final int RING_BUFFER_SIZE = 2048;

	private final AtomicReference<CountDownLatch> latchRef = new AtomicReference<>();

	private Disruptor<OrderEvent> disruptor;
	private RingBuffer<OrderEvent> ringBuffer;

	@PostConstruct
	public void start() {
		disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
				ProducerType.SINGLE, new BlockingWaitStrategy());
		disruptor.handleEventsWith(this::onEvent);
		ringBuffer = disruptor.start();
	}

	@PreDestroy
	public void shutdown() {
		disruptor.shutdown();
	}

	public SingleHandlerResult process(int eventCount) {
		CountDownLatch latch = new CountDownLatch(eventCount);
		latchRef.set(latch);

		long start = System.currentTimeMillis();
		for (long i = 0; i < eventCount; i++) {
			ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
		}
		await(latch);
		long elapsed = System.currentTimeMillis() - start;

		return new SingleHandlerResult(eventCount, elapsed, throughputPerSecond(eventCount, elapsed));
	}

	private void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
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

	private double throughputPerSecond(long events, long elapsedMillis) {
		if (elapsedMillis == 0) {
			return events;
		}
		return events * 1000.0 / elapsedMillis;
	}
}
