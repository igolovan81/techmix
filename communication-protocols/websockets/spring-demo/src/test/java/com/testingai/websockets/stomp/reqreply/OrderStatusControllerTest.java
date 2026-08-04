package com.testingai.websockets.stomp.reqreply;

import com.testingai.websockets.domain.Order;
import com.testingai.websockets.domain.OrderStatus;
import com.testingai.websockets.domain.OrderTrackingService;
import com.testingai.websockets.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderStatusControllerTest {

	private final OrderTrackingService orderTrackingService = mock(OrderTrackingService.class);
	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
	private final OrderStatusController controller = new OrderStatusController(orderTrackingService, messagingTemplate);

	@Test
	void statusRequest_repliesToSenderSessionQueue_withCurrentOrderState() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);
			Order order = new Order("order-1", OrderStatus.SHIPPED, Instant.now());
			when(orderTrackingService.get("order-1")).thenReturn(order);
			SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
			headerAccessor.setSessionId("session-42");

			controller.statusRequest("order-1", headerAccessor);

			verify(messagingTemplate).convertAndSendToUser(eq("session-42"), eq("/queue/orders/order-1/status"), any(),
					any(Map.class));
		}
	}

	@Test
	void statusRequest_propagatesException_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));
			SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
			headerAccessor.setSessionId("session-42");

			assertThatThrownBy(() -> controller.statusRequest("order-1", headerAccessor))
					.isInstanceOf(RuntimeException.class).hasMessage("Simulated");
		}
	}
}
