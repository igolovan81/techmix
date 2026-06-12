package com.testingai.sqs.pubsub;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PubSubSubscriberB {

    @SqsListener(QueueNames.PUBSUB_B)
    public void receive(String message) {
        log.info("[pubsub][subscriber-B] received: {}", message);
    }
}
