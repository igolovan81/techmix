package com.testingai.sqs.pubsub;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PubSubPublisher {

    private final SnsTemplate snsTemplate;

    public void publish(String message) {
        snsTemplate.convertAndSend(QueueNames.PUBSUB_TOPIC_ARN, message);
        log.info("[pubsub] published: {}", message);
    }
}
