package com.testingai.servicebus.routing;

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
public class RoutingConsumerError implements ApplicationRunner {

	private final ServiceBusClientBuilder clientBuilder;
	private ServiceBusProcessorClient processorClient;

	public RoutingConsumerError(ServiceBusClientBuilder clientBuilder) {
		this.clientBuilder = clientBuilder;
	}

	@Override
	public void run(ApplicationArguments args) {
		processorClient = clientBuilder.processor().topicName(EntityNames.ROUTING_TOPIC)
				.subscriptionName(EntityNames.ROUTING_SUB_ERROR).processMessage(ctx -> {
					log.info("[routing][sub-error] received: {}", ctx.getMessage().getBody());
					ctx.complete();
				}).processError(ctx -> log.error("[routing][sub-error] error", ctx.getException()))
				.buildProcessorClient();
		processorClient.start();
	}

	@PreDestroy
	public void close() {
		processorClient.close();
	}
}
