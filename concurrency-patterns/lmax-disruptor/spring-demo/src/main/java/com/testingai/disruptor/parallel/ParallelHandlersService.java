package com.testingai.disruptor.parallel;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ParallelHandlersService {

	private static final int RING_BUFFER_SIZE = 2048;

	private final AtomicLong journalCount = new AtomicLong();
	private final AtomicLong riskCheckCount = new AtomicLong();
	private final AtomicReference<CountDownLatch> journalLatchRef = new AtomicReference<>();
	private final AtomicReference<CountDownLatch> riskCheckLatchRef = new AtomicReference<>();

	private Disruptor<OrderEvent> disruptor;
	private RingBuffer<OrderEvent> ringBuffer;

	@PostConstruct
	public void start() {
		disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
				ProducerType.SINGLE, new BlockingWaitStrategy());
		disruptor.handleEventsWith(new JournalHandler(journalCount, journalLatchRef),
				new RiskCheckHandler(riskCheckCount, riskCheckLatchRef));
		ringBuffer = disruptor.start();
	}

	@PreDestroy
	public void shutdown() {
		disruptor.shutdown();
	}

	public ParallelResult process(int eventCount) {
		journalCount.set(0);
		riskCheckCount.set(0);
		journalLatchRef.set(new CountDownLatch(eventCount));
		riskCheckLatchRef.set(new CountDownLatch(eventCount));

		long start = System.currentTimeMillis();
		for (long i = 0; i < eventCount; i++) {
			ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
		}
		await(journalLatchRef.get());
		await(riskCheckLatchRef.get());
		long elapsed = System.currentTimeMillis() - start;

		return new ParallelResult(journalCount.get(), riskCheckCount.get(), elapsed);
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
