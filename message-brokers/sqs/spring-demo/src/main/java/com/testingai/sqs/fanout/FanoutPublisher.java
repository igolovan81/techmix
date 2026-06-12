package com.testingai.sqs.fanout;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FanoutPublisher {

    private final SnsTemplate snsTemplate;

    public void publish(String message) {
        snsTemplate.convertAndSend(QueueNames.FANOUT_TOPIC_ARN, message);
        log.info("[fanout] published: {}", message);
    }
}
