package com.testingai.saga.choreography;

import org.junit.jupiter.api.Test;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.COMPENSATED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

class SagaLogTest {

	private final SagaLog sagaLog = new SagaLog();

	@Test
	void timelineFor_shouldReturnEmptyListForUnknownOrder() {
		assertThat(sagaLog.timelineFor("missing")).isEmpty();
	}

	@Test
	void append_shouldAccumulateEntriesInOrderPerOrderId() {
		sagaLog.append("order-1", "ORDER_CREATED", SUCCEEDED, null);
		sagaLog.append("order-1", "INVENTORY_RESERVED", SUCCEEDED, null);
		sagaLog.append("order-2", "ORDER_CREATED", SUCCEEDED, null);

		assertThat(sagaLog.timelineFor("order-1")).extracting(SagaLogEntry::step)
				.containsExactly("ORDER_CREATED", "INVENTORY_RESERVED");
		assertThat(sagaLog.timelineFor("order-2")).extracting(SagaLogEntry::step).containsExactly("ORDER_CREATED");
	}

	@Test
	void append_shouldRecordOutcomeAndDetail() {
		sagaLog.append("order-1", "INVENTORY_RELEASED", COMPENSATED, "payment failed upstream");

		assertThat(sagaLog.timelineFor("order-1")).singleElement().satisfies(entry -> {
			assertThat(entry.outcome()).isEqualTo(COMPENSATED);
			assertThat(entry.detail()).isEqualTo("payment failed upstream");
		});
	}
}
