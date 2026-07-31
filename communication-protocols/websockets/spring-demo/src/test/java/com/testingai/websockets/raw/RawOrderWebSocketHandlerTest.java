package com.testingai.websockets.raw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RawOrderWebSocketHandlerTest {

	private final RawOrderWebSocketHandler handler = new RawOrderWebSocketHandler(
			new ObjectMapper().registerModule(new JavaTimeModule()));

	@Test
	void publish_sendsEventToAllOpenSessions() throws Exception {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("session-1");
		handler.afterConnectionEstablished(session);

		handler.publish(new OrderEvent("order-1", OrderStatus.PAID, Instant.now()));

		verify(session).sendMessage(any(TextMessage.class));
	}

	@Test
	void publish_skipsSessions_afterConnectionClosed() throws Exception {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("session-2");
		handler.afterConnectionEstablished(session);
		handler.afterConnectionClosed(session, CloseStatus.NORMAL);

		handler.publish(new OrderEvent("order-2", OrderStatus.PAID, Instant.now()));

		verify(session, never()).sendMessage(any(TextMessage.class));
	}
}
