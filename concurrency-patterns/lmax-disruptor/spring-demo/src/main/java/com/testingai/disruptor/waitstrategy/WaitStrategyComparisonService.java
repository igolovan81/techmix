package com.testingai.disruptor.waitstrategy;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WaitStrategyComparisonService {

	private static final int RING_BUFFER_SIZE = 2048;

	public List<WaitStrategyStat> compare(int eventCount) {
		List<WaitStrategyStat> stats = new ArrayList<>();
		stats.add(run("BLOCKING", new BlockingWaitStrategy(), eventCount));
		stats.add(run("YIELDING", new YieldingWaitStrategy(), eventCount));
		stats.add(run("BUSY_SPIN", new BusySpinWaitStrategy(), eventCount));
		stats.sort((a, b) -> Double.compare(a.avgLatencyMicros(), b.avgLatencyMicros()));
		return stats;
	}

	private WaitStrategyStat run(String strategyName, WaitStrategy waitStrategy, int eventCount) {
		Disruptor<OrderEvent> disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE,
				DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, waitStrategy);
		CountDownLatch latch = new CountDownLatch(eventCount);
		AtomicLong processed = new AtomicLong();
		AtomicLong totalLatencyNanos = new AtomicLong();
		disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
			totalLatencyNanos.addAndGet(System.nanoTime() - event.getPublishNanos());
			processed.incrementAndGet();
			latch.countDown();
		});
		RingBuffer<OrderEvent> ringBuffer = disruptor.start();

		try {
			long start = System.currentTimeMillis();
			for (long i = 0; i < eventCount; i++) {
				ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
			}
			latch.await(30, TimeUnit.SECONDS);
			long elapsed = System.currentTimeMillis() - start;
			long processedCount = processed.get();
			double avgLatencyMicros = processedCount == 0 ? 0 : totalLatencyNanos.get() / 1000.0 / processedCount;
			return new WaitStrategyStat(strategyName, processedCount, elapsed,
					throughputPerSecond(processedCount, elapsed), avgLatencyMicros);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for wait-strategy comparison run", e);
		} finally {
			disruptor.shutdown();
		}
	}

	private double throughputPerSecond(long events, long elapsedMillis) {
		if (elapsedMillis == 0) {
			return events;
		}
		return events * 1000.0 / elapsedMillis;
	}
}
