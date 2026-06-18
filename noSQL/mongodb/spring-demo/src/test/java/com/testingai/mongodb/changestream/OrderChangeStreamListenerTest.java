package com.testingai.mongodb.changestream;

import com.testingai.mongodb.transaction.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.Message;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderChangeStreamListenerTest {

	@InjectMocks
	private OrderChangeStreamListener listener;

	@Mock
	private MongoTemplate mongoTemplate;

	@Test
	void onChange_shouldNotThrowForAnOrderEvent() {
		Order order = new Order("o1", "p1", 2, 10.0, 20.0, "PLACED");
		@SuppressWarnings("unchecked")
		Message<?, Order> message = mock(Message.class);
		when(message.getBody()).thenReturn(order);

		assertThatCode(() -> listener.onChange(message)).doesNotThrowAnyException();
	}
}
