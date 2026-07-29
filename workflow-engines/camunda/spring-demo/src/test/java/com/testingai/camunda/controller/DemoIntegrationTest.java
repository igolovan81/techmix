package com.testingai.camunda.controller;

import com.testingai.camunda.domain.CheckoutRequest;
import com.testingai.camunda.domain.OrderLine;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import com.testingai.camunda.domain.OrderView;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byProcessInstanceKey;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
class DemoIntegrationTest {

	@LocalServerPort
	private int port;

	private final TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void lowValueOrder_completesWithoutApproval() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(10.00))), null);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		long processInstanceKey = response.getBody().processInstanceKey();

		assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
				.hasCompletedElementsInOrder(byId("StartEvent_OrderPlaced"), byId("ServiceTask_ReserveInventory"),
						byId("Gateway_HighValueOrder"), byId("ServiceTask_ProcessPayment"),
						byId("ServiceTask_ArrangeShipping"), byId("EndEvent_OrderFulfilled"))
				.isCompleted();
	}

	@Test
	void highValueOrder_requiresApproval_thenCompletesWhenApproved() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(999.00))), null);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);
		String orderId = response.getBody().orderId();
		long processInstanceKey = response.getBody().processInstanceKey();

		assertThatUserTask(byProcessInstanceKey(processInstanceKey)).isCreated();

		restTemplate.postForEntity("http://localhost:" + port + "/demo/camunda/orders/" + orderId + "/approval",
				new ApprovalRequest(true), Void.class);

		assertThatUserTask(byProcessInstanceKey(processInstanceKey)).isCompleted();
		assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey)).isCompleted();
	}

	@Test
	void highValueOrder_cancelsWhenRejected() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(999.00))), null);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);
		String orderId = response.getBody().orderId();

		restTemplate.postForEntity("http://localhost:" + port + "/demo/camunda/orders/" + orderId + "/approval",
				new ApprovalRequest(false), Void.class);

		assertThat(awaitOrderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void order_isCancelled_whenInventoryReservationFailsAt() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(10.00))), OrderStep.RESERVE_INVENTORY);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);

		assertThat(awaitOrderStatus(response.getBody().orderId())).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void order_isCancelled_whenPaymentFailsAt() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(10.00))), OrderStep.PROCESS_PAYMENT);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);

		assertThat(awaitOrderStatus(response.getBody().orderId())).isEqualTo(OrderStatus.CANCELLED);
	}

	/**
	 * Job workers process asynchronously on their own polling thread, so a {@code GET} taken immediately after
	 * starting/advancing a process instance can race the worker updating {@link com.testingai.camunda.domain.OrderReadModel}.
	 * Polls for up to 5 seconds rather than assuming zero latency between "process instance advanced" and "read model
	 * updated."
	 */
	private OrderStatus awaitOrderStatus(String orderId) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
		OrderStatus lastSeen = null;
		while (Instant.now().isBefore(deadline)) {
			ResponseEntity<OrderView> orderView = restTemplate
					.getForEntity("http://localhost:" + port + "/demo/camunda/orders/" + orderId, OrderView.class);
			lastSeen = orderView.getBody().status();
			if (lastSeen == OrderStatus.CANCELLED || lastSeen == OrderStatus.FULFILLED) {
				return lastSeen;
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return lastSeen;
			}
		}
		return lastSeen;
	}
}
