package com.testingai.servicebus.dlq;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.models.SubQueue;
import com.testingai.servicebus.config.EntityNames;
import com.testingai.servicebus.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DlqConsumer implements ApplicationRunner {

    private final ServiceBusClientBuilder clientBuilder;

    public DlqConsumer(ServiceBusClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ServiceBusProcessorClient mainProcessor = clientBuilder
                .processor()
                .queueName(EntityNames.DLQ_QUEUE)
                .processMessage(ctx -> {
                    if (FailureSimulator.shouldFail()) {
                        log.warn("[dlq] simulating failure for: {}", ctx.getMessage().getBody().toString());
                        ctx.abandon();
                    } else {
                        log.info("[dlq] processed: {}", ctx.getMessage().getBody().toString());
                        ctx.complete();
                    }
                })
                .processError(ctx -> log.error("[dlq] error", ctx.getException()))
                .buildProcessorClient();
        mainProcessor.start();

        ServiceBusProcessorClient dlqProcessor = clientBuilder
                .processor()
                .queueName(EntityNames.DLQ_QUEUE)
                .subQueue(SubQueue.DEAD_LETTER_QUEUE)
                .processMessage(ctx -> {
                    log.warn("[dlq] dead-lettered after {} attempts: {}",
                            ctx.getMessage().getDeliveryCount(),
                            ctx.getMessage().getBody().toString());
                    ctx.complete();
                })
                .processError(ctx -> log.error("[dlq] DLQ processor error", ctx.getException()))
                .buildProcessorClient();
        dlqProcessor.start();
    }
}
