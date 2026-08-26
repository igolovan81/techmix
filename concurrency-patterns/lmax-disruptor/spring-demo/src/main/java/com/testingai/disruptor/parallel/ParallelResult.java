package com.testingai.disruptor.parallel;

public record ParallelResult(long journalCount, long riskCheckCount, long elapsedMillis) {
}
