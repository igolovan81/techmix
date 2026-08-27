package com.testingai.batch.launch;

public record JobRunResult(Long jobExecutionId, String jobName, String status, int readCount, int writeCount,
		int skipCount, long durationMillis) {
}
