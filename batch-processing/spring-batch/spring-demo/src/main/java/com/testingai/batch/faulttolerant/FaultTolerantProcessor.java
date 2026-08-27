package com.testingai.batch.faulttolerant;

import com.testingai.batch.domain.Invoice;
import com.testingai.batch.domain.InvoiceCalculator;
import com.testingai.batch.domain.Order;
import com.testingai.batch.util.FailureSimulator;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class FaultTolerantProcessor implements ItemProcessor<Order, Invoice> {

	@Override
	public Invoice process(Order order) {
		FailureSimulator.maybeThrow("fault-tolerant-invoice");
		return InvoiceCalculator.toInvoice(order);
	}
}
