package com.testingai.servicebus.pubsub;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.testingai.servicebus.config.EntityNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PubSubSubscriberA implements ApplicationRunner {

    private final ServiceBusClientBuilder clientBuilder;

    public PubSubSubscriberA(ServiceBusClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ServiceBusProcessorClient processorClient = clientBuilder
                .processor()
                .topicName(EntityNames.PUBSUB_TOPIC)
                .subscriptionName(EntityNames.PUBSUB_SUB_A)
                .processMessage(ctx -> {
                    log.info("[pubsub][sub-a] received: {}", ctx.getMessage().getBody().toString());
                    ctx.complete();
                })
                .processError(ctx -> log.error("[pubsub][sub-a] error", ctx.getException()))
                .buildProcessorClient();
        processorClient.start();
    }
}
