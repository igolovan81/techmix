package com.testingai.sqs.workqueue;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkQueueProducer {

	private final SqsTemplate sqsTemplate;

	public void send(String message) {
		sqsTemplate.send(QueueNames.WORK, message);
		log.info("[work-queue] sent: {}", message);
	}
}
