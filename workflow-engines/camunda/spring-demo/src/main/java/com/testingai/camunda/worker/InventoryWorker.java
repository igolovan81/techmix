package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryWorker {

	private final OrderReadModel orderReadModel;

	@JobWorker(type = "reserve-inventory")
	public Map<String, Object> reserveInventory(ActivatedJob job) {
		String orderId = (String) job.getVariablesAsMap().get("orderId");
		String failAt = (String) job.getVariablesAsMap().get("failAt");
		log.info("[reserve-inventory] orderId={}", orderId);
		if (OrderStep.RESERVE_INVENTORY.name().equals(failAt)) {
			log.warn("[reserve-inventory] simulated failure for orderId={}", orderId);
			orderReadModel.updateStatus(orderId, OrderStatus.CANCELLED);
			throw new BpmnError("INVENTORY_UNAVAILABLE", "No stock available for order " + orderId);
		}
		orderReadModel.recordStepCompleted(orderId, OrderStep.RESERVE_INVENTORY);
		return Map.of("inventoryReserved", true);
	}

	@JobWorker(type = "release-inventory")
	public Map<String, Object> releaseInventory(ActivatedJob job) {
		String orderId = (String) job.getVariablesAsMap().get("orderId");
		log.info("[release-inventory] releasing reserved inventory for orderId={}", orderId);
		orderReadModel.updateStatus(orderId, OrderStatus.CANCELLED);
		return Map.of("inventoryReserved", false);
	}
}
