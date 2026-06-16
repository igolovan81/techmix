package com.testingai.redis.fanout;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import java.util.Map;

class FanoutConsumerATest {
	@Test
	void onMessageLogsPayload() {
		var consumer = new FanoutConsumerA();
		var record = MapRecord.create("test-stream", Map.of("message", "broadcast"));
		consumer.onMessage(record);
	}
}
