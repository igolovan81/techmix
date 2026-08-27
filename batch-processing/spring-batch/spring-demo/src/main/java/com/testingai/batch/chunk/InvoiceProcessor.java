package com.testingai.batch.chunk;

import com.testingai.batch.domain.Invoice;
import com.testingai.batch.domain.InvoiceCalculator;
import com.testingai.batch.domain.Order;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class InvoiceProcessor implements ItemProcessor<Order, Invoice> {

	@Override
	public Invoice process(Order order) {
		return InvoiceCalculator.toInvoice(order);
	}
}
