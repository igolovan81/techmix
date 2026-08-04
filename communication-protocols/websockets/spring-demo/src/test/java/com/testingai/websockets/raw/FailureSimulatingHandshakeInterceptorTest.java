package com.testingai.websockets.raw;

import com.testingai.websockets.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class FailureSimulatingHandshakeInterceptorTest {

	private final FailureSimulatingHandshakeInterceptor interceptor = new FailureSimulatingHandshakeInterceptor();
	private final ServerHttpRequest request = mock(ServerHttpRequest.class);
	private final ServerHttpResponse response = mock(ServerHttpResponse.class);
	private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

	@Test
	void beforeHandshake_returnsTrue_whenNoSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			boolean result = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

			assertThat(result).isTrue();
		}
	}

	@Test
	void beforeHandshake_returnsFalse_andSets503_whenSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			boolean result = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

			assertThat(result).isFalse();
			verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		}
	}
}
