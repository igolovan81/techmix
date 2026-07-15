package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.PaymentProcessed;
import com.testingai.saga.choreography.event.ShipmentArranged;
import com.testingai.saga.choreography.event.ShipmentFailed;
import com.testingai.saga.domain.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.FAILED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;

@Component
@RequiredArgsConstructor
public class ShippingParticipant {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;

	@EventListener
	public void onPaymentProcessed(PaymentProcessed event) {
		if (event.failAt() == SagaStep.ARRANGE_SHIPPING) {
			String reason = "carrier unavailable (simulated)";
			sagaLog.append(event.orderId(), "SHIPMENT_FAILED", FAILED, reason);
			publisher.publishEvent(new ShipmentFailed(event.orderId(), reason));
			return;
		}
		sagaLog.append(event.orderId(), "SHIPMENT_ARRANGED", SUCCEEDED, null);
		publisher.publishEvent(new ShipmentArranged(event.orderId()));
	}
}
