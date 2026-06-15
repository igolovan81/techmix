package com.testingai.servicebus.simple;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.testingai.servicebus.config.EntityNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SimpleConsumer implements ApplicationRunner {

    private final ServiceBusClientBuilder clientBuilder;

    public SimpleConsumer(ServiceBusClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ServiceBusProcessorClient processorClient = clientBuilder
                .processor()
                .queueName(EntityNames.SIMPLE_QUEUE)
                .processMessage(ctx -> {
                    log.info("[simple] received: {}", ctx.getMessage().getBody().toString());
                    ctx.complete();
                })
                .processError(ctx -> log.error("[simple] error", ctx.getException()))
                .buildProcessorClient();
        processorClient.start();
    }
}
