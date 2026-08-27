package com.testingai.batch.restart;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class RestartFailureTracker {

	private static final int FAIL_ON_ITEM_NUMBER = 5;

	private final ConcurrentHashMap<String, AtomicInteger> itemsProcessed = new ConcurrentHashMap<>();

	/**
	 * Returns true exactly once per runId — on whichever call happens to be the 5th process() call for that runId,
	 * across however many launches it takes to get there. The counter never resets, so once it passes 5 it can never
	 * equal 5 again for the same runId, which is what makes this a one-time failure rather than a repeating one.
	 */
	public boolean shouldFailNow(String runId) {
		int itemNumber = itemsProcessed.computeIfAbsent(runId, id -> new AtomicInteger(0)).incrementAndGet();
		return itemNumber == FAIL_ON_ITEM_NUMBER;
	}
}
