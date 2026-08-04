package com.testingai.websockets.domain;

@FunctionalInterface
public interface OrderEventPublisher {

	void publish(OrderEvent event);
}
