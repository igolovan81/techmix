package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.InventoryReleased;
import com.testingai.saga.choreography.event.InventoryReserved;
import com.testingai.saga.choreography.event.InventoryReservationFailed;
import com.testingai.saga.choreography.event.OrderCreated;
import com.testingai.saga.choreography.event.PaymentFailed;
import com.testingai.saga.choreography.event.PaymentRefunded;
import com.testingai.saga.domain.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.COMPENSATED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.FAILED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;

@Component
@RequiredArgsConstructor
public class InventoryParticipant {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;
	private final Set<String> reservedOrderIds = ConcurrentHashMap.newKeySet();

	@EventListener
	public void onOrderCreated(OrderCreated event) {
		if (event.failAt() == SagaStep.RESERVE_INVENTORY) {
			String reason = "insufficient stock (simulated)";
			sagaLog.append(event.orderId(), "INVENTORY_RESERVATION_FAILED", FAILED, reason);
			publisher.publishEvent(new InventoryReservationFailed(event.orderId(), reason));
			return;
		}
		reservedOrderIds.add(event.orderId());
		sagaLog.append(event.orderId(), "INVENTORY_RESERVED", SUCCEEDED, null);
		publisher.publishEvent(new InventoryReserved(event.orderId(), event.failAt()));
	}

	@EventListener
	public void onPaymentFailed(PaymentFailed event) {
		release(event.orderId());
	}

	@EventListener
	public void onPaymentRefunded(PaymentRefunded event) {
		release(event.orderId());
	}

	public boolean hasReservation(String orderId) {
		return reservedOrderIds.contains(orderId);
	}

	private void release(String orderId) {
		reservedOrderIds.remove(orderId);
		sagaLog.append(orderId, "INVENTORY_RELEASED", COMPENSATED, null);
		publisher.publishEvent(new InventoryReleased(orderId));
	}
}
