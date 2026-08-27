package com.testingai.batch.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceCalculatorTest {

	@Test
	void toInvoice_shouldComputeTaxAndTotal() {
		Order order = new Order(1L, BatchType.CHUNK, "cust-1", new BigDecimal("100.00"), OrderStatus.PENDING, null);

		Invoice invoice = InvoiceCalculator.toInvoice(order);

		assertThat(invoice.getOrderId()).isEqualTo(1L);
		assertThat(invoice.getCustomerId()).isEqualTo("cust-1");
		assertThat(invoice.getAmount()).isEqualByComparingTo("100.00");
		assertThat(invoice.getTax()).isEqualByComparingTo("8.00");
		assertThat(invoice.getTotal()).isEqualByComparingTo("108.00");
	}
}
