package com.testingai.sqs.fifo;

import com.testingai.sqs.config.QueueNames;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FifoProducer {

    private final SqsTemplate sqsTemplate;

    public void send(String message, String groupId) {
        sqsTemplate.send(to -> to
                .queue(QueueNames.FIFO)
                .payload(message)
                .header("message-group-id", groupId));
        log.info("[fifo] sent group={} message={}", groupId, message);
    }
}
