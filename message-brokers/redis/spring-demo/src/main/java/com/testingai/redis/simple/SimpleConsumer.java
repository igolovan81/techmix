package com.testingai.redis.simple;

import com.testingai.redis.config.StreamKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

@Slf4j
public class SimpleConsumer implements StreamListener<String, MapRecord<String, String, String>> {

	public SimpleConsumer() {
	}

	public SimpleConsumer(StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
		container.receive(StreamOffset.create(StreamKeys.SIMPLE, ReadOffset.lastConsumed()), this);
	}

	@Override
	public void onMessage(MapRecord<String, String, String> record) {
		log.info("[simple] received id={} body={}", record.getId(), record.getValue());
	}
}
