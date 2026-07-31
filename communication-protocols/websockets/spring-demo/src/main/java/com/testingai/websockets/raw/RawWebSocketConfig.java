package com.testingai.websockets.raw;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

	private final RawOrderWebSocketHandler rawOrderWebSocketHandler;

	public RawWebSocketConfig(RawOrderWebSocketHandler rawOrderWebSocketHandler) {
		this.rawOrderWebSocketHandler = rawOrderWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(rawOrderWebSocketHandler, "/ws/raw/orders")
				.addInterceptors(new FailureSimulatingHandshakeInterceptor()).setAllowedOrigins("*");
	}
}
