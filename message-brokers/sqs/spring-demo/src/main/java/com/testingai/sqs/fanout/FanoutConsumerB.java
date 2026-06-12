package com.testingai.sqs.fanout;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FanoutConsumerB {

    @SqsListener(QueueNames.FANOUT_B)
    public void receive(String message) {
        log.info("[fanout][consumer-B] received: {}", message);
    }
}
