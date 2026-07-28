package com.testingai.reactor.resilience;

public record BackpressureResultDto(String strategy, long emitted, long processed, long droppedOrBuffered) {
}
