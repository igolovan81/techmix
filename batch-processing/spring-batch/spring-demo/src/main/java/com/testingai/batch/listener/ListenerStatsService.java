package com.testingai.batch.listener;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

@Service
public class ListenerStatsService {

	private final AtomicReference<ListenerStats> latest = new AtomicReference<>();

	public void record(ListenerStats stats) {
		latest.set(stats);
	}

	public ListenerStats getLatest() {
		return latest.get();
	}
}
