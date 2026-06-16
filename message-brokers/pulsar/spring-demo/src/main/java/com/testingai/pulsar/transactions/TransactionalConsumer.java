package com.testingai.pulsar.transactions;

import com.testingai.pulsar.config.TopicNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionalConsumer {

	@PulsarListener(topics = TopicNames.TX_TOPIC, subscriptionName = "tx-sub")
	public void receive(String message) {
		log.info("[transaction] received: {}", message);
	}
}
