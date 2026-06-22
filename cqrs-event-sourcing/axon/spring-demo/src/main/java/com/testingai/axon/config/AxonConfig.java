package com.testingai.axon.config;

import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AxonConfig {

	private static final int SNAPSHOT_EVENT_COUNT_THRESHOLD = 5;

	@Bean
	public SnapshotTriggerDefinition orderSnapshotTriggerDefinition(Snapshotter snapshotter) {
		return new EventCountSnapshotTriggerDefinition(snapshotter, SNAPSHOT_EVENT_COUNT_THRESHOLD);
	}
}
