package com.testingai.servicebus.pubsub;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.testingai.servicebus.config.EntityNames;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PubSubSubscriberB implements ApplicationRunner {

	private final ServiceBusClientBuilder clientBuilder;
	private ServiceBusProcessorClient processorClient;

	public PubSubSubscriberB(ServiceBusClientBuilder clientBuilder) {
		this.clientBuilder = clientBuilder;
	}

	@Override
	public void run(ApplicationArguments args) {
		processorClient = clientBuilder.processor().topicName(EntityNames.PUBSUB_TOPIC)
				.subscriptionName(EntityNames.PUBSUB_SUB_B).processMessage(ctx -> {
					log.info("[pubsub][sub-b] received: {}", ctx.getMessage().getBody());
					ctx.complete();
				}).processError(ctx -> log.error("[pubsub][sub-b] error", ctx.getException())).buildProcessorClient();
		processorClient.start();
	}

	@PreDestroy
	public void close() {
		processorClient.close();
	}
}
