package com.testingai.saga.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRequestTest {

	@Test
	void checkoutRequest_shouldExposeCustomerItemsAndFailAt() {
		OrderLine line = new OrderLine("p1", 2, new BigDecimal("9.99"));
		CheckoutRequest request = new CheckoutRequest("customer-1", List.of(line), SagaStep.PROCESS_PAYMENT);

		assertThat(request.customerId()).isEqualTo("customer-1");
		assertThat(request.items()).containsExactly(line);
		assertThat(request.failAt()).isEqualTo(SagaStep.PROCESS_PAYMENT);
	}

	@Test
	void checkoutRequest_shouldAllowNullFailAtForHappyPath() {
		CheckoutRequest request = new CheckoutRequest("customer-1", List.of(), null);

		assertThat(request.failAt()).isNull();
	}
}
