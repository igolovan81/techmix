package com.testingai.camunda.controller;

import com.testingai.camunda.domain.CheckoutRequest;
import com.testingai.camunda.domain.OrderLine;
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

		ResponseEntity<OrderView> orderView = restTemplate
				.getForEntity("http://localhost:" + port + "/demo/camunda/orders/" + orderId, OrderView.class);
		assertThat(orderView.getBody().status()).isEqualTo(com.testingai.camunda.domain.OrderStatus.CANCELLED);
	}
}
