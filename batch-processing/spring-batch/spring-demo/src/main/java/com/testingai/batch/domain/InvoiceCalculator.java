package com.testingai.batch.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class InvoiceCalculator {

	private static final BigDecimal TAX_RATE = new BigDecimal("0.08");

	private InvoiceCalculator() {
	}

	public static Invoice toInvoice(Order order) {
		BigDecimal tax = order.getAmount().multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
		BigDecimal total = order.getAmount().add(tax);

		Invoice invoice = new Invoice();
		invoice.setOrderId(order.getId());
		invoice.setCustomerId(order.getCustomerId());
		invoice.setAmount(order.getAmount());
		invoice.setTax(tax);
		invoice.setTotal(total);
		return invoice;
	}
}
