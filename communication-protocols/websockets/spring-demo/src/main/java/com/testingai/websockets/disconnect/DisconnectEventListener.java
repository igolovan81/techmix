package com.testingai.websockets.disconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class DisconnectEventListener {

	private static final Logger log = LoggerFactory.getLogger(DisconnectEventListener.class);

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		log.info(describe(event));
	}

	String describe(SessionDisconnectEvent event) {
		return "STOMP session " + event.getSessionId() + " disconnected, closeStatus=" + event.getCloseStatus();
	}
}
