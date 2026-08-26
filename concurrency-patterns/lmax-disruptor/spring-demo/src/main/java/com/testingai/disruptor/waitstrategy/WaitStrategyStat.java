package com.testingai.disruptor.waitstrategy;

public record WaitStrategyStat(String strategyName, long eventsProcessed, long elapsedMillis,
		double throughputPerSecond, double avgLatencyMicros) {
}
