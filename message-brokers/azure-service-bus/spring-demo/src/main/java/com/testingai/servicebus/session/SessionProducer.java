package com.testingai.servicebus.session;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.testingai.servicebus.config.EntityNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SessionProducer {

	private final ServiceBusSenderClient senderClient;

	public SessionProducer(ServiceBusClientBuilder clientBuilder) {
		this.senderClient = clientBuilder.sender().queueName(EntityNames.SESSION_QUEUE).buildClient();
	}

	public void send(String message, String sessionId) {
		ServiceBusMessage msg = new ServiceBusMessage(message);
		msg.setSessionId(sessionId);
		senderClient.sendMessage(msg);
		log.info("[session] sessionId={} sent={}", sessionId, message);
	}
}
