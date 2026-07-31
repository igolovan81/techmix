package com.testingai.websockets.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// /ws-stomp: SockJS-wrapped, used by the browser test client for fallback compatibility.
		registry.addEndpoint("/ws-stomp").setAllowedOriginPatterns("*").withSockJS();
		// /ws-stomp-native: plain STOMP-over-WebSocket, used by Gatling and integration tests so they can speak
		// STOMP directly without parsing the SockJS frame envelope.
		registry.addEndpoint("/ws-stomp-native").setAllowedOriginPatterns("*");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
		heartbeatScheduler.setPoolSize(1);
		heartbeatScheduler.setThreadNamePrefix("stomp-heartbeat-");
		heartbeatScheduler.initialize();

		registry.enableSimpleBroker("/topic", "/queue").setHeartbeatValue(new long[]{10000, 10000})
				.setTaskScheduler(heartbeatScheduler);
		registry.setApplicationDestinationPrefixes("/app");
	}
}
