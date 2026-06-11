package com.testingai.kafka.compaction;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class CompactionConsumerTest {

    @InjectMocks
    private CompactionConsumer consumer;

    @Test
    void receive_shouldNotThrow() {
        var record = new ConsumerRecord<>("compacted.topic", 0, 0L, "user-1", "Alice");
        assertThatCode(() -> consumer.receive(record)).doesNotThrowAnyException();
    }
}
