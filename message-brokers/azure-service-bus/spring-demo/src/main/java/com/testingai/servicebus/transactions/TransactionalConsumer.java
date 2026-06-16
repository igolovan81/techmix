package com.testingai.servicebus.transactions;

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
public class TransactionalConsumer implements ApplicationRunner {

	private final ServiceBusClientBuilder clientBuilder;
	private ServiceBusProcessorClient processorClient;

	public TransactionalConsumer(ServiceBusClientBuilder clientBuilder) {
		this.clientBuilder = clientBuilder;
	}

	@Override
	public void run(ApplicationArguments args) {
		processorClient = clientBuilder.processor().queueName(EntityNames.TX_QUEUE).processMessage(ctx -> {
			log.info("[transaction] received: {}", ctx.getMessage().getBody());
			ctx.complete();
		}).processError(ctx -> log.error("[transaction] error", ctx.getException())).buildProcessorClient();
		processorClient.start();
	}

	@PreDestroy
	public void close() {
		processorClient.close();
	}
}
