package com.testingai.sqs.fifo;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FifoConsumer {

    @SqsListener(QueueNames.FIFO)
    public void receive(String message) {
        log.info("[fifo] received (ordered): {}", message);
    }
}
