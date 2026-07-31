package com.testingai.websockets.controller;

import com.testingai.websockets.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RawWebSocketIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void broadcast_reachesConnectedRawClient_afterAdvance() throws Exception {
		BlockingQueue<String> received = new ArrayBlockingQueue<>(10);
		StandardWebSocketClient client = new StandardWebSocketClient();
		WebSocketHandler handler = new TextWebSocketHandler() {
			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) {
				received.add(message.getPayload());
			}
		};
		WebSocketSession session = client.execute(handler, "ws://localhost:" + port + "/ws/raw/orders").get(5,
				TimeUnit.SECONDS);

		try {
			Order created = restTemplate.postForObject("/api/orders", null, Order.class);
			restTemplate.postForObject("/api/orders/" + created.id() + "/advance", null, Order.class);

			String message = received.poll(5, TimeUnit.SECONDS);

			assertThat(message).isNotNull().contains(created.id()).contains("PAID");
		} finally {
			session.close();
		}
	}
}
