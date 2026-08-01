package com.testingai.websockets.disconnect;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DisconnectEventListenerTest {

	private final DisconnectEventListener listener = new DisconnectEventListener();

	@Test
	void describe_includesSessionIdAndCloseStatus() {
		SessionDisconnectEvent event = disconnectEvent("session-99", CloseStatus.GOING_AWAY);

		String description = listener.describe(event);

		assertThat(description).contains("session-99").contains("1001");
	}

	@Test
	void onDisconnect_doesNotThrow() {
		SessionDisconnectEvent event = disconnectEvent("session-100", CloseStatus.NORMAL);

		listener.onDisconnect(event);
	}

	@SuppressWarnings("unchecked")
	private SessionDisconnectEvent disconnectEvent(String sessionId, CloseStatus closeStatus) {
		Message<byte[]> message = mock(Message.class);
		return new SessionDisconnectEvent(this, message, sessionId, closeStatus);
	}
}
