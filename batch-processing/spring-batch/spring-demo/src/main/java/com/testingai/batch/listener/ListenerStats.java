package com.testingai.batch.listener;

import java.time.LocalDateTime;

public record ListenerStats(String jobName, String status, LocalDateTime startTime, LocalDateTime endTime,
		long durationMillis, int readCount, int writeCount, int skipCount) {
}
