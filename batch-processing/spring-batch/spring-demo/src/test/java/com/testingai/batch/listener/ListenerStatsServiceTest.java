package com.testingai.batch.listener;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListenerStatsServiceTest {

	private final ListenerStatsService listenerStatsService = new ListenerStatsService();

	@Test
	void getLatest_shouldReturnNullBeforeAnyRecord() {
		assertThat(listenerStatsService.getLatest()).isNull();
	}

	@Test
	void record_shouldOverwritePreviousStats() {
		ListenerStats first = new ListenerStats("job", "COMPLETED", LocalDateTime.now(), LocalDateTime.now(), 100, 5, 5,
				0);
		ListenerStats second = new ListenerStats("job", "FAILED", LocalDateTime.now(), LocalDateTime.now(), 50, 2, 1,
				1);

		listenerStatsService.record(first);
		listenerStatsService.record(second);

		assertThat(listenerStatsService.getLatest()).isEqualTo(second);
	}
}
