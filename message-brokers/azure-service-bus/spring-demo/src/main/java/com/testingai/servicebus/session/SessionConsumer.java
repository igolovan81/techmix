package com.testingai.servicebus.session;

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
public class SessionConsumer implements ApplicationRunner {

	private final ServiceBusClientBuilder clientBuilder;
	private ServiceBusProcessorClient processorClient;

	public SessionConsumer(ServiceBusClientBuilder clientBuilder) {
		this.clientBuilder = clientBuilder;
	}

	@Override
	public void run(ApplicationArguments args) {
		processorClient = clientBuilder.sessionProcessor().queueName(EntityNames.SESSION_QUEUE).maxConcurrentSessions(2)
				.processMessage(ctx -> {
					log.info("[session] sessionId={} received={}", ctx.getMessage().getSessionId(),
							ctx.getMessage().getBody());
					ctx.complete();
				}).processError(ctx -> log.error("[session] error", ctx.getException())).buildProcessorClient();
		processorClient.start();
	}

	@PreDestroy
	public void close() {
		processorClient.close();
	}
}
