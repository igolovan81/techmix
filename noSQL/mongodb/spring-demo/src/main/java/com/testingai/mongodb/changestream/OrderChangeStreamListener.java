package com.testingai.mongodb.changestream;

import com.testingai.mongodb.transaction.Order;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderChangeStreamListener {

	private final MongoTemplate mongoTemplate;
	private MessageListenerContainer container;

	@PostConstruct
	public void start() {
		container = new DefaultMessageListenerContainer(mongoTemplate);
		container.start();
		ChangeStreamRequest<Order> request = ChangeStreamRequest.builder(this::onChange).collection("orders").build();
		container.register(request, Order.class);
	}

	public void onChange(Message<?, Order> message) {
		Order order = message.getBody();
		log.info("[OrderChangeStreamListener] Order changed: {}", order);
	}

	@PreDestroy
	public void stop() {
		container.stop();
	}
}
