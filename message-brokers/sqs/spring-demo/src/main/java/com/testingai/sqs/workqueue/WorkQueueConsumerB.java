package com.testingai.sqs.workqueue;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkQueueConsumerB {

	@SqsListener(QueueNames.WORK)
	public void receive(String message) {
		log.info("[work-queue][consumer-B] received: {}", message);
	}
}
