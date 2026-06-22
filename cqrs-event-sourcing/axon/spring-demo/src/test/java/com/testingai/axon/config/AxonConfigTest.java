package com.testingai.axon.config;

import com.testingai.axon.command.OrderAggregate;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AxonConfigTest {

	@Mock
	private Snapshotter snapshotter;

	@Test
	void orderSnapshotTriggerDefinition_shouldReturnEventCountTrigger() {
		AxonConfig config = new AxonConfig();

		SnapshotTriggerDefinition triggerDefinition = config.orderSnapshotTriggerDefinition(snapshotter);

		assertThat(triggerDefinition).isInstanceOf(EventCountSnapshotTriggerDefinition.class);
	}

	@Test
	void orderAggregate_shouldReferenceSnapshotTriggerDefinitionBeanByName() {
		Aggregate annotation = OrderAggregate.class.getAnnotation(Aggregate.class);

		assertThat(annotation.snapshotTriggerDefinition()).isEqualTo("orderSnapshotTriggerDefinition");
	}
}
