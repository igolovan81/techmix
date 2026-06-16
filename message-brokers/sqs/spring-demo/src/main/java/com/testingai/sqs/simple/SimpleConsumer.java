package com.testingai.sqs.simple;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SimpleConsumer {

	@SqsListener(QueueNames.SIMPLE)
	public void receive(String message) {
		log.info("[simple] received: {}", message);
	}
}
