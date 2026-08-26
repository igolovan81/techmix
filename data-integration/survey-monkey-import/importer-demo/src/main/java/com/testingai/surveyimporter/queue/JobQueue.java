package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.domain.SyncJob;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.DelayQueue;

@Component
public class JobQueue {

	private final DelayQueue<DelayedSyncJob> queue = new DelayQueue<>();

	public void enqueue(SyncJob job) {
		enqueue(job, Duration.ZERO);
	}

	public void enqueue(SyncJob job, Duration delay) {
		queue.put(new DelayedSyncJob(job, delay));
	}

	public SyncJob take() throws InterruptedException {
		return queue.take().job();
	}

	public int size() {
		return queue.size();
	}
}
