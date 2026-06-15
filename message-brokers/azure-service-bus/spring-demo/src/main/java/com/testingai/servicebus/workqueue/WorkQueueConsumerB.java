package com.testingai.servicebus.workqueue;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.testingai.servicebus.config.EntityNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkQueueConsumerB implements ApplicationRunner {

    private final ServiceBusClientBuilder clientBuilder;

    public WorkQueueConsumerB(ServiceBusClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ServiceBusProcessorClient processorClient = clientBuilder
                .processor()
                .queueName(EntityNames.WORK_QUEUE)
                .processMessage(ctx -> {
                    log.info("[work][B] received: {}", ctx.getMessage().getBody().toString());
                    ctx.complete();
                })
                .processError(ctx -> log.error("[work][B] error", ctx.getException()))
                .buildProcessorClient();
        processorClient.start();
    }
}
