package com.testingai.disruptor.diamond;

import com.lmax.disruptor.EventHandler;
import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.matching.OrderMatchingEngine;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class MatchingHandler implements EventHandler<OrderEvent> {

	private final OrderMatchingEngine matchingEngine;
	private final List<Fill> fills;
	private final AtomicReference<CountDownLatch> latchRef;

	public MatchingHandler(OrderMatchingEngine matchingEngine, List<Fill> fills,
			AtomicReference<CountDownLatch> latchRef) {
		this.matchingEngine = matchingEngine;
		this.fills = fills;
		this.latchRef = latchRef;
	}

	@Override
	public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
		fills.addAll(matchingEngine.match(event));
		CountDownLatch latch = latchRef.get();
		if (latch != null) {
			latch.countDown();
		}
	}
}
