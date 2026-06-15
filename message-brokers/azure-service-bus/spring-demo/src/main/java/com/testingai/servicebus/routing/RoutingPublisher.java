package com.testingai.servicebus.routing;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.testingai.servicebus.config.EntityNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RoutingPublisher {

    private final ServiceBusSenderClient senderClient;

    public RoutingPublisher(ServiceBusClientBuilder clientBuilder) {
        this.senderClient = clientBuilder
                .sender()
                .topicName(EntityNames.ROUTING_TOPIC)
                .buildClient();
    }

    public void publish(String level, String message) {
        ServiceBusMessage msg = new ServiceBusMessage(message);
        msg.getApplicationProperties().put(EntityNames.ROUTING_KEY, level);
        senderClient.sendMessage(msg);
        log.info("[routing] level={} sent={}", level, message);
    }
}
