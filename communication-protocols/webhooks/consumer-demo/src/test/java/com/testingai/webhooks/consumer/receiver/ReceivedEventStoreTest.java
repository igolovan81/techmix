package com.testingai.webhooks.consumer.receiver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivedEventStoreTest {

	private final ReceivedEventStore store = new ReceivedEventStore();

	@Test
	void recordIfNew_returnsTrue_andRecordsNonDuplicate_forFirstOccurrence() {
		boolean isNew = store.recordIfNew("d1", "order.created", "order-1");

		assertThat(isNew).isTrue();
		assertThat(store.all()).extracting(ReceivedEvent::duplicate).containsExactly(false);
	}

	@Test
	void recordIfNew_returnsFalse_andRecordsDuplicate_forRepeatedDeliveryId() {
		store.recordIfNew("d1", "order.created", "order-1");

		boolean isNew = store.recordIfNew("d1", "order.created", "order-1");

		assertThat(isNew).isFalse();
		assertThat(store.all()).extracting(ReceivedEvent::duplicate).containsExactly(false, true);
	}
}
