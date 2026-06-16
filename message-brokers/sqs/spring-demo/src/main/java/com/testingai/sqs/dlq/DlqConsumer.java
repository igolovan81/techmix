package com.testingai.sqs.dlq;

import com.testingai.sqs.config.QueueNames;
import com.testingai.sqs.util.FailureSimulator;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DlqConsumer {

	/**
	 * Fails ~50 % of the time. SQS redelivers up to maxReceiveCount=3 times; after that the message is moved to
	 * retry-dlq automatically.
	 */
	@SqsListener(QueueNames.RETRY)
	public void receive(String message) {
		if (FailureSimulator.shouldFail()) {
			log.warn("[retry] simulating failure for: {}", message);
			throw new RuntimeException("simulated processing failure");
		}
		log.info("[retry] processed: {}", message);
	}

	@SqsListener(QueueNames.RETRY_DLQ)
	public void receiveDlq(String message) {
		log.warn("[retry-dlq] dead-lettered after 3 retries: {}", message);
	}
}
