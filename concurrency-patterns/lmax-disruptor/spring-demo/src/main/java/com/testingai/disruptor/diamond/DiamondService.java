package com.testingai.disruptor.diamond;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;
import com.testingai.disruptor.matching.OrderMatchingEngine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DiamondService {

	private static final int RING_BUFFER_SIZE = 2048;

	private final OrderMatchingEngine matchingEngine = new OrderMatchingEngine();
	private final List<Fill> fills = new CopyOnWriteArrayList<>();
	private final AtomicLong journalCount = new AtomicLong();
	private final AtomicLong replicationCount = new AtomicLong();
	private final AtomicReference<CountDownLatch> latchRef = new AtomicReference<>();

	private Disruptor<OrderEvent> disruptor;
	private RingBuffer<OrderEvent> ringBuffer;

	@PostConstruct
	public void start() {
		disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
				ProducerType.SINGLE, new BlockingWaitStrategy());
		disruptor.handleEventsWith(new JournalHandler(journalCount), new ReplicationHandler(replicationCount))
				.then(new MatchingHandler(matchingEngine, fills, latchRef));
		ringBuffer = disruptor.start();
	}

	@PreDestroy
	public void shutdown() {
		disruptor.shutdown();
	}

	public DiamondResult process(int eventCount) {
		fills.clear();
		latchRef.set(new CountDownLatch(eventCount));

		long start = System.currentTimeMillis();
		for (long i = 0; i < eventCount; i++) {
			ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
		}
		await(latchRef.get());
		long elapsed = System.currentTimeMillis() - start;

		return new DiamondResult(List.copyOf(fills), matchingEngine.restingOrderCount(), elapsed);
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
