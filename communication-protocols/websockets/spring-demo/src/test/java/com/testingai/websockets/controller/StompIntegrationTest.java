package com.testingai.websockets.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.websockets.domain.Order;
import com.testingai.websockets.domain.OrderEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private StompSession connect() throws Exception {
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setObjectMapper(new ObjectMapper().registerModule(new JavaTimeModule()));
		stompClient.setMessageConverter(converter);
		return stompClient
				.connectAsync("ws://localhost:" + port + "/ws-stomp-native", new StompSessionHandlerAdapter() {
				}).get(5, TimeUnit.SECONDS);
	}

	private StompFrameHandler collectingHandler(BlockingQueue<OrderEvent> received) {
		return new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return OrderEvent.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((OrderEvent) payload);
			}
		};
	}

	@Test
	void broadcastTopic_receivesEvent_afterAdvance() throws Exception {
		BlockingQueue<OrderEvent> received = new ArrayBlockingQueue<>(10);
		StompSession session = connect();
		session.subscribe("/topic/orders", collectingHandler(received));

		try {
			Order created = restTemplate.postForObject("/api/orders", null, Order.class);
			restTemplate.postForObject("/api/orders/" + created.id() + "/advance", null, Order.class);

			OrderEvent event = received.poll(5, TimeUnit.SECONDS);

			assertThat(event).isNotNull();
			assertThat(event.orderId()).isEqualTo(created.id());
		} finally {
			session.disconnect();
		}
	}

	@Test
	void perOrderTopic_receivesEvent_forSubscribedOrderOnly() throws Exception {
		BlockingQueue<OrderEvent> received = new ArrayBlockingQueue<>(10);
		StompSession session = connect();
		Order created = restTemplate.postForObject("/api/orders", null, Order.class);
		session.subscribe("/topic/orders/" + created.id(), collectingHandler(received));

		try {
			restTemplate.postForObject("/api/orders/" + created.id() + "/advance", null, Order.class);

			OrderEvent event = received.poll(5, TimeUnit.SECONDS);

			assertThat(event).isNotNull();
			assertThat(event.orderId()).isEqualTo(created.id());
		} finally {
			session.disconnect();
		}
	}

	@Test
	void statusRequest_repliesOnPrivateQueue_withCurrentOrderState() throws Exception {
		BlockingQueue<OrderEvent> received = new ArrayBlockingQueue<>(10);
		StompSession session = connect();
		Order created = restTemplate.postForObject("/api/orders", null, Order.class);
		session.subscribe("/user/queue/orders/" + created.id() + "/status", collectingHandler(received));

		try {
			// FailureSimulator has a real 5% chance of dropping any single status-request; retry a few times so
			// the test isn't flaky (probability all 5 attempts fail is 0.05^5, i.e. negligible).
			OrderEvent event = null;
			for (int attempt = 0; attempt < 5 && event == null; attempt++) {
				session.send("/app/orders/" + created.id() + "/status-request", null);
				event = received.poll(2, TimeUnit.SECONDS);
			}

			assertThat(event).isNotNull();
			assertThat(event.orderId()).isEqualTo(created.id());
			assertThat(event.status().name()).isEqualTo("CREATED");
		} finally {
			session.disconnect();
		}
	}
}
