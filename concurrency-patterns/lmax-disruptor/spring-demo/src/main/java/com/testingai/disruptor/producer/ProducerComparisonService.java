package com.testingai.disruptor.producer;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProducerComparisonService {

	private static final int RING_BUFFER_SIZE = 2048;

	public List<ProducerStat> compare(int eventCount, int threadCount) {
		List<ProducerStat> stats = new ArrayList<>();
		stats.add(run(ProducerType.SINGLE, eventCount, 1));
		stats.add(run(ProducerType.MULTI, eventCount, Math.max(1, threadCount)));
		return stats;
	}

	private ProducerStat run(ProducerType producerType, int eventCount, int publisherThreads) {
		Disruptor<OrderEvent> disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE,
				DaemonThreadFactory.INSTANCE, producerType, new BlockingWaitStrategy());
		CountDownLatch latch = new CountDownLatch(eventCount);
		AtomicLong processed = new AtomicLong();
		disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
			processed.incrementAndGet();
			latch.countDown();
		});
		RingBuffer<OrderEvent> ringBuffer = disruptor.start();

		try {
			long start = System.currentTimeMillis();
			publish(ringBuffer, eventCount, publisherThreads);
			latch.await(30, TimeUnit.SECONDS);
			long elapsed = System.currentTimeMillis() - start;
			return new ProducerStat(producerType.name(), publisherThreads, processed.get(), elapsed,
					throughputPerSecond(processed.get(), elapsed));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for producer comparison run", e);
		} finally {
			disruptor.shutdown();
		}
	}

	private void publish(RingBuffer<OrderEvent> ringBuffer, int eventCount, int publisherThreads) {
		if (publisherThreads == 1) {
			for (long i = 0; i < eventCount; i++) {
				ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
			}
			return;
		}

		int perThread = eventCount / publisherThreads;
		int remainder = eventCount % publisherThreads;
		List<Runnable> tasks = new ArrayList<>();
		long nextIndex = 0;
		for (int t = 0; t < publisherThreads; t++) {
			long fromIndex = nextIndex;
			int count = perThread + (t < remainder ? 1 : 0);
			long toIndex = fromIndex + count;
			tasks.add(() -> {
				for (long i = fromIndex; i < toIndex; i++) {
					ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
				}
			});
			nextIndex = toIndex;
		}

		ExecutorService executor = Executors.newFixedThreadPool(publisherThreads);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (Runnable task : tasks) {
				futures.add(executor.submit(task));
			}
			for (Future<?> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while publishing", e);
		} catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException("Publisher task failed", e);
		} finally {
			executor.shutdown();
		}
	}

	private double throughputPerSecond(long events, long elapsedMillis) {
		if (elapsedMillis == 0) {
			return events;
		}
		return events * 1000.0 / elapsedMillis;
	}
}
