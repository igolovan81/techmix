package com.testingai.disruptor.producer;

public record ProducerStat(String producerType, int threadCount, long eventsProcessed, long elapsedMillis,
		double throughputPerSecond) {
}
