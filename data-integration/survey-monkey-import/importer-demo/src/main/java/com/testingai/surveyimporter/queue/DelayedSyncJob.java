package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.domain.SyncJob;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

final class DelayedSyncJob implements Delayed {

	private final SyncJob job;
	private final long readyAtNanos;

	DelayedSyncJob(SyncJob job, Duration delay) {
		this.job = job;
		this.readyAtNanos = System.nanoTime() + delay.toNanos();
	}

	SyncJob job() {
		return job;
	}

	@Override
	public long getDelay(TimeUnit unit) {
		return unit.convert(readyAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
	}

	@Override
	public int compareTo(Delayed other) {
		return Long.compare(this.readyAtNanos, ((DelayedSyncJob) other).readyAtNanos);
	}
}
