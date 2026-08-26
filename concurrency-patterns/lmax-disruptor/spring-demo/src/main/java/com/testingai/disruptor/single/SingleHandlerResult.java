package com.testingai.disruptor.single;

public record SingleHandlerResult(long eventsProcessed, long elapsedMillis, double throughputPerSecond) {
}
