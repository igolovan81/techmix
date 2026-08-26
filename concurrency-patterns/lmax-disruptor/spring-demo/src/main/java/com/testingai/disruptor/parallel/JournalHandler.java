package com.testingai.disruptor.parallel;

import com.lmax.disruptor.EventHandler;
import com.testingai.disruptor.domain.OrderEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class JournalHandler implements EventHandler<OrderEvent> {

	private final AtomicLong counter;
	private final AtomicReference<CountDownLatch> latchRef;

	public JournalHandler(AtomicLong counter, AtomicReference<CountDownLatch> latchRef) {
		this.counter = counter;
		this.latchRef = latchRef;
	}

	@Override
	public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
		counter.incrementAndGet();
		CountDownLatch latch = latchRef.get();
		if (latch != null) {
			latch.countDown();
		}
	}
}
