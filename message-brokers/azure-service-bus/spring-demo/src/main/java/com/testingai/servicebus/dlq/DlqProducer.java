package com.testingai.servicebus.dlq;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.testingai.servicebus.config.EntityNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DlqProducer {

	private final ServiceBusSenderClient senderClient;

	public DlqProducer(ServiceBusClientBuilder clientBuilder) {
		this.senderClient = clientBuilder.sender().queueName(EntityNames.DLQ_QUEUE).buildClient();
	}

	public void send(String message) {
		senderClient.sendMessage(new ServiceBusMessage(message));
		log.info("[dlq] sent: {}", message);
	}
}
