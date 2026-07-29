package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentWorkerTest {

	private final OrderReadModel orderReadModel = new OrderReadModel();
	private final PaymentWorker worker = new PaymentWorker(orderReadModel);

	@Test
	void processPayment_recordsStep_whenNoFailAt() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1"));

		worker.processPayment(job);

		assertThat(orderReadModel.find("o1").orElseThrow().completedSteps()).containsExactly(OrderStep.PROCESS_PAYMENT);
	}

	@Test
	void processPayment_throwsPaymentDeclined_whenFailAtMatches() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1", "failAt", "PROCESS_PAYMENT"));

		assertThatThrownBy(() -> worker.processPayment(job)).isInstanceOf(BpmnError.class)
				.satisfies(ex -> assertThat(((BpmnError) ex).getErrorCode()).isEqualTo("PAYMENT_DECLINED"));
	}
}
