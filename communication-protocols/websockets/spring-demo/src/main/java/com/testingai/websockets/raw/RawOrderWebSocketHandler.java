package com.testingai.websockets.raw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RawOrderWebSocketHandler extends TextWebSocketHandler implements OrderEventPublisher {

	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper;

	public RawOrderWebSocketHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.put(session.getId(), session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session.getId());
	}

	@Override
	public void publish(OrderEvent event) {
		sessions.values().forEach(session -> sendQuietly(session, event));
	}

	private void sendQuietly(WebSocketSession session, OrderEvent event) {
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
		} catch (IOException e) {
			sessions.remove(session.getId());
		}
	}
}
