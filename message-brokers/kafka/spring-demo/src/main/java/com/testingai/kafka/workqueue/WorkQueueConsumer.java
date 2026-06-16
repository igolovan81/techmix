package com.testingai.kafka.workqueue;

import com.testingai.kafka.config.TopicConfig;
import com.testingai.kafka.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WorkQueueConsumer {

	@KafkaListener(topics = TopicConfig.WORK_TOPIC, groupId = "work-group", id = "worker1")
	public void worker1(String message) {
		log.info("[Worker1] Processing: {}", message);
		FailureSimulator.maybeThrow("[Worker1]");
		simulateWork(message);
		log.info("[Worker1] Done: {}", message);
	}

	@KafkaListener(topics = TopicConfig.WORK_TOPIC, groupId = "work-group", id = "worker2")
	public void worker2(String message) {
		log.info("[Worker2] Processing: {}", message);
		FailureSimulator.maybeThrow("[Worker2]");
		simulateWork(message);
		log.info("[Worker2] Done: {}", message);
	}

	private void simulateWork(String message) {
		long dots = message.chars().filter(c -> c == '.').count();
		try {
			Thread.sleep(dots * 1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
