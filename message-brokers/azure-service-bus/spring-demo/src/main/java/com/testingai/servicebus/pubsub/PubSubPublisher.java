package com.testingai.servicebus.pubsub;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.testingai.servicebus.config.EntityNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PubSubPublisher {

	private final ServiceBusSenderClient senderClient;

	public PubSubPublisher(ServiceBusClientBuilder clientBuilder) {
		this.senderClient = clientBuilder.sender().topicName(EntityNames.PUBSUB_TOPIC).buildClient();
	}

	public void publish(String message) {
		senderClient.sendMessage(new ServiceBusMessage(message));
		log.info("[pubsub] published: {}", message);
	}
}
