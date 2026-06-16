package com.testingai.pulsar.simple;

import com.testingai.pulsar.config.TopicNames;
import com.testingai.pulsar.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SimpleConsumer {

	@PulsarListener(topics = TopicNames.SIMPLE_TOPIC, subscriptionName = "simple-sub")
	public void receive(String message) {
		FailureSimulator.maybeThrow("[simple]");
		log.info("[simple] received: {}", message);
	}
}
