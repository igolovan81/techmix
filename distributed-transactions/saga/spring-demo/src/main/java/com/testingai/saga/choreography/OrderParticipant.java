package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.CheckoutRequested;
import com.testingai.saga.choreography.event.InventoryReleased;
import com.testingai.saga.choreography.event.InventoryReservationFailed;
import com.testingai.saga.choreography.event.OrderCancelled;
import com.testingai.saga.choreography.event.OrderConfirmed;
import com.testingai.saga.choreography.event.OrderCreated;
import com.testingai.saga.choreography.event.ShipmentArranged;
import com.testingai.saga.domain.SagaStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.FAILED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;

@Component
@RequiredArgsConstructor
public class OrderParticipant {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;
	private final Map<String, SagaStatus> statusByOrderId = new ConcurrentHashMap<>();

	@EventListener
	public void onCheckoutRequested(CheckoutRequested event) {
		statusByOrderId.put(event.orderId(), SagaStatus.PENDING);
		sagaLog.append(event.orderId(), "ORDER_CREATED", SUCCEEDED, null);
		publisher.publishEvent(new OrderCreated(event.orderId(), event.items(), event.failAt()));
	}

	@EventListener
	public void onShipmentArranged(ShipmentArranged event) {
		statusByOrderId.put(event.orderId(), SagaStatus.CONFIRMED);
		sagaLog.append(event.orderId(), "ORDER_CONFIRMED", SUCCEEDED, null);
		publisher.publishEvent(new OrderConfirmed(event.orderId()));
	}

	@EventListener
	public void onInventoryReservationFailed(InventoryReservationFailed event) {
		cancel(event.orderId(), event.reason());
	}

	@EventListener
	public void onInventoryReleased(InventoryReleased event) {
		cancel(event.orderId(), "compensated after a downstream step failed");
	}

	public SagaStatus statusOf(String orderId) {
		return statusByOrderId.get(orderId);
	}

	private void cancel(String orderId, String reason) {
		statusByOrderId.put(orderId, SagaStatus.CANCELLED);
		sagaLog.append(orderId, "ORDER_CANCELLED", FAILED, reason);
		publisher.publishEvent(new OrderCancelled(orderId, reason));
	}
}
