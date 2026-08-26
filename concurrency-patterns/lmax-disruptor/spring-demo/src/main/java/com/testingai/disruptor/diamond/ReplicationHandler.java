package com.testingai.disruptor.diamond;

import com.lmax.disruptor.EventHandler;
import com.testingai.disruptor.domain.OrderEvent;

import java.util.concurrent.atomic.AtomicLong;

public class ReplicationHandler implements EventHandler<OrderEvent> {

	private final AtomicLong counter;

	public ReplicationHandler(AtomicLong counter) {
		this.counter = counter;
	}

	@Override
	public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
		counter.incrementAndGet();
	}
}
