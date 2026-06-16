package com.testingai.sqs.dlq;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqProducer {

	private final SqsTemplate sqsTemplate;

	public void send(String message) {
		sqsTemplate.send(QueueNames.RETRY, message);
		log.info("[retry] sent: {}", message);
	}
}
