package com.testingai.redis.simple;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;

import java.util.Map;

class SimpleConsumerTest {

	@Test
	void onMessageLogsPayload() {
		var consumer = new SimpleConsumer();
		var record = MapRecord.create("test-stream", Map.of("message", "hello"));
		// No exception thrown = pass
		consumer.onMessage(record);
	}
}
