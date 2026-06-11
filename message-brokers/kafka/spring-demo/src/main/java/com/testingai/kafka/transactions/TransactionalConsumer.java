package com.testingai.kafka.transactions;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionalConsumer {

    @KafkaListener(topics = TopicConfig.TX_OUTPUT_TOPIC, groupId = "tx-group",
                   containerFactory = "transactionalContainerFactory")
    public void receive(String message) {
        log.info("[TransactionalConsumer] read_committed: {}", message);
    }
}
