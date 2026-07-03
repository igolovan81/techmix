package com.testingai.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TopicConfigTest {

	private TopicConfig config;

	@BeforeEach
	void setUp() {
		config = new TopicConfig();
		ReflectionTestUtils.setField(config, "replicationFactor", 1);
	}

	@Test
	void simpleTopic_hasOnePartition() {
		NewTopic topic = config.simpleTopic();
		assertThat(topic.name()).isEqualTo("simple.topic");
		assertThat(topic.numPartitions()).isEqualTo(1);
	}

	@Test
	void workTopic_hasThreePartitions() {
		NewTopic topic = config.workTopic();
		assertThat(topic.name()).isEqualTo("work.topic");
		assertThat(topic.numPartitions()).isEqualTo(3);
	}

	@Test
	void compactedTopic_hasCompactPolicy() {
		NewTopic topic = config.compactedTopic();
		assertThat(topic.configs()).containsEntry("cleanup.policy", "compact");
	}
}
