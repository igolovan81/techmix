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
public class RoutingConsumerAll implements ApplicationRunner {

	private final ServiceBusClientBuilder clientBuilder;
	private ServiceBusProcessorClient processorClient;

	public RoutingConsumerAll(ServiceBusClientBuilder clientBuilder) {
		this.clientBuilder = clientBuilder;
	}

	@Override
	public void run(ApplicationArguments args) {
		processorClient = clientBuilder.processor().topicName(EntityNames.ROUTING_TOPIC)
				.subscriptionName(EntityNames.ROUTING_SUB_ALL).processMessage(ctx -> {
					String level = String
							.valueOf(ctx.getMessage().getApplicationProperties().get(EntityNames.ROUTING_KEY));
					log.info("[routing][sub-all] level={} received={}", level, ctx.getMessage().getBody());
					ctx.complete();
				}).processError(ctx -> log.error("[routing][sub-all] error", ctx.getException()))
				.buildProcessorClient();
		processorClient.start();
	}

	@PreDestroy
	public void close() {
		processorClient.close();
	}
}
