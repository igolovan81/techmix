package com.testingai.kafka.transactions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalProducerLegacyTest {

	@Mock
	private KafkaTemplate<String, String> transactionalKafkaTemplate;

	@InjectMocks
	private TransactionalProducer producer;

	@Test
	@SuppressWarnings("unchecked")
	void send_shouldCallExecuteInTransaction() {
		when(transactionalKafkaTemplate.executeInTransaction(any())).thenReturn(null);
		producer.send("hello", 3);
		verify(transactionalKafkaTemplate).executeInTransaction(any());
	}
}
