package com.testingai.pulsar.transactions;

import com.testingai.pulsar.config.TopicNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionalProducer {

	private final PulsarTemplate<String> pulsarTemplate;

	// Pulsar supports native transactions (PulsarClient.newTransaction()) when
	// transactionCoordinatorEnabled=true on the broker. This demo uses the
	// template for brevity — wire in PulsarTransactionManager for full ACID guarantees.
	public void send(String message, int count) {
		for (int i = 0; i < count; i++) {
			pulsarTemplate.send(TopicNames.TX_TOPIC, message + "-" + i);
		}
		log.info("[transaction] sent {} messages to tx-topic", count);
	}
}
