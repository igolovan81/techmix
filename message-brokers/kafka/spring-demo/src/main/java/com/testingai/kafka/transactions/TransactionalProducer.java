package com.testingai.kafka.transactions;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionalProducer {

    private final KafkaTemplate<String, String> transactionalKafkaTemplate;

    public TransactionalProducer(
            @Qualifier("transactionalKafkaTemplate") KafkaTemplate<String, String> transactionalKafkaTemplate) {
        this.transactionalKafkaTemplate = transactionalKafkaTemplate;
    }

    public void send(String message, int count) {
        transactionalKafkaTemplate.executeInTransaction(ops -> {
            for (int i = 0; i < count; i++) {
                ops.send(TopicConfig.TX_OUTPUT_TOPIC, "tx-key-" + i, message + "-" + i);
            }
            log.info("[TransactionalProducer] Committed {} messages in one transaction", count);
            return null;
        });
    }
}
