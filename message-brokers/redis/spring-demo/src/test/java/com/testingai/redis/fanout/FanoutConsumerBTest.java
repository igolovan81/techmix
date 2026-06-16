package com.testingai.redis.fanout;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import java.util.Map;

class FanoutConsumerBTest {
	@Test
	void onMessageLogsPayload() {
		var consumer = new FanoutConsumerB();
		var record = MapRecord.create("test-stream", Map.of("message", "broadcast"));
		consumer.onMessage(record);
	}
}
