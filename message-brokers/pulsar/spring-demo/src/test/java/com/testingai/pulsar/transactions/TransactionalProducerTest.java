package com.testingai.pulsar.transactions;

import com.testingai.pulsar.config.TopicNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.pulsar.core.PulsarTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionalProducerTest {

	@Mock
	private PulsarTemplate<String> pulsarTemplate;

	@InjectMocks
	private TransactionalProducer producer;

	@Test
	void send_shouldSendCountMessagesToTxTopic() {
		producer.send("hello", 3);
		verify(pulsarTemplate, times(3)).send(eq(TopicNames.TX_TOPIC), startsWith("hello-"));
	}
}
