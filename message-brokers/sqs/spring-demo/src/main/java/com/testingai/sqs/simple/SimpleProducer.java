package com.testingai.sqs.simple;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimpleProducer {

	private final SqsTemplate sqsTemplate;

	public void send(String message) {
		sqsTemplate.send(QueueNames.SIMPLE, message);
		log.info("[simple] sent: {}", message);
	}
}
