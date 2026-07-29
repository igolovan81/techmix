package com.testingai.camunda.controller;

import com.testingai.camunda.domain.CheckoutRequest;
import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderView;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Starts and observes order-fulfillment process instances. Job workers ({@code InventoryWorker},
 * {@code PaymentWorker}, {@code ShippingWorker}) do the actual step-by-step work and update {@link OrderReadModel} as
 * they go; this controller only starts instances and reads that model back.
 */
@Slf4j
@RestController
@RequestMapping("/demo/camunda")
@RequiredArgsConstructor
public class DemoController {

	private static final long HIGH_VALUE_THRESHOLD_CENTS = 50_000;
	private static final String PROCESS_ID = "order-fulfillment";

	private final CamundaClient camundaClient;
	private final OrderReadModel orderReadModel;

	@PostMapping("/orders")
	public ResponseEntity<StartOrderResponse> startOrder(@RequestBody CheckoutRequest request) {
		String orderId = UUID.randomUUID().toString();
		long totalCents = request.items().stream().mapToLong(
				item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())).movePointRight(2).longValueExact())
				.sum();

		Map<String, Object> variables = new HashMap<>();
		variables.put("orderId", orderId);
		variables.put("customerId", request.customerId());
		variables.put("totalCents", totalCents);
		if (request.failAt() != null) {
			variables.put("failAt", request.failAt().name());
		}

		OrderStatus initialStatus = totalCents > HIGH_VALUE_THRESHOLD_CENTS ? OrderStatus.PENDING_APPROVAL
				: OrderStatus.IN_PROGRESS;

		ProcessInstanceEvent instance = camundaClient.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID)
				.latestVersion().variables(variables).execute();

		orderReadModel.register(orderId, instance.getProcessInstanceKey(), initialStatus);
		log.info("[startOrder] orderId={} processInstanceKey={} totalCents={}", orderId,
				instance.getProcessInstanceKey(), totalCents);
		return ResponseEntity.ok(new StartOrderResponse(orderId, instance.getProcessInstanceKey()));
	}

	@PostMapping("/orders/{orderId}/approval")
	public ResponseEntity<Void> approveOrder(@PathVariable String orderId, @RequestBody ApprovalRequest request) {
		OrderView order = orderReadModel.find(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

		UserTask userTask = camundaClient.newUserTaskSearchRequest()
				.filter(f -> f.processInstanceKey(order.processInstanceKey()).state(UserTaskState.CREATED)).execute()
				.singleItem();

		camundaClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).variable("approved", request.approved())
				.execute();

		log.info("[approveOrder] orderId={} approved={}", orderId, request.approved());
		return ResponseEntity.ok().build();
	}

	@GetMapping("/orders/{orderId}")
	public ResponseEntity<OrderView> getOrder(@PathVariable String orderId) {
		return orderReadModel.find(orderId).map(ResponseEntity::ok)
				.orElseThrow(() -> new OrderNotFoundException(orderId));
	}
}
