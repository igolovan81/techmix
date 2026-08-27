package com.testingai.batch.restart;

import com.testingai.batch.domain.Invoice;
import com.testingai.batch.domain.InvoiceCalculator;
import com.testingai.batch.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
@RequiredArgsConstructor
public class RestartProcessor implements ItemProcessor<Order, Invoice> {

	private final RestartFailureTracker restartFailureTracker;

	@Value("#{jobParameters['runId']}")
	private String runId;

	@Override
	public Invoice process(Order order) {
		if (restartFailureTracker.shouldFailNow(runId)) {
			throw new RuntimeException("Simulated failure for restart demo, runId=" + runId);
		}
		return InvoiceCalculator.toInvoice(order);
	}
}
