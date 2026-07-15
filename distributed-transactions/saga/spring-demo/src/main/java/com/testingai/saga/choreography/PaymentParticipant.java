package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.InventoryReserved;
import com.testingai.saga.choreography.event.PaymentFailed;
import com.testingai.saga.choreography.event.PaymentProcessed;
import com.testingai.saga.choreography.event.PaymentRefunded;
import com.testingai.saga.choreography.event.ShipmentFailed;
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
public class PaymentParticipant {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;
	private final Set<String> chargedOrderIds = ConcurrentHashMap.newKeySet();

	@EventListener
	public void onInventoryReserved(InventoryReserved event) {
		if (event.failAt() == SagaStep.PROCESS_PAYMENT) {
			String reason = "card declined (simulated)";
			sagaLog.append(event.orderId(), "PAYMENT_FAILED", FAILED, reason);
			publisher.publishEvent(new PaymentFailed(event.orderId(), reason));
			return;
		}
		chargedOrderIds.add(event.orderId());
		sagaLog.append(event.orderId(), "PAYMENT_PROCESSED", SUCCEEDED, null);
		publisher.publishEvent(new PaymentProcessed(event.orderId(), event.failAt()));
	}

	@EventListener
	public void onShipmentFailed(ShipmentFailed event) {
		chargedOrderIds.remove(event.orderId());
		sagaLog.append(event.orderId(), "PAYMENT_REFUNDED", COMPENSATED, null);
		publisher.publishEvent(new PaymentRefunded(event.orderId()));
	}

	public boolean hasCharge(String orderId) {
		return chargedOrderIds.contains(orderId);
	}
}
