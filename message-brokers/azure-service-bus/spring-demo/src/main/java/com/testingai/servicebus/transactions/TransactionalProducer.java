package com.testingai.servicebus.transactions;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.ServiceBusTransactionContext;
import com.testingai.servicebus.config.EntityNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionalProducer {

	private final ServiceBusSenderClient senderClient;

	public TransactionalProducer(ServiceBusClientBuilder clientBuilder) {
		this.senderClient = clientBuilder.sender().queueName(EntityNames.TX_QUEUE).buildClient();
	}

	public void send(String message, int count) {
		ServiceBusTransactionContext transaction = senderClient.createTransaction();
		try {
			for (int i = 0; i < count; i++) {
				senderClient.sendMessage(new ServiceBusMessage(message + "-" + i), transaction);
			}
			senderClient.commitTransaction(transaction);
			log.info("[transaction] committed {} messages", count);
		} catch (Exception e) {
			senderClient.rollbackTransaction(transaction);
			log.error("[transaction] rolled back", e);
			throw e;
		}
	}
}
