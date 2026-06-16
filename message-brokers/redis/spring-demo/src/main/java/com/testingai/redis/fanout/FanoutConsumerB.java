package com.testingai.redis.fanout;

import com.testingai.redis.config.StreamKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

@Slf4j
public class FanoutConsumerB implements StreamListener<String, MapRecord<String, String, String>> {

	public FanoutConsumerB() {
	}

	public FanoutConsumerB(StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
		container.receive(Consumer.from("group-b", "consumer-b"),
				StreamOffset.create(StreamKeys.FANOUT, ReadOffset.lastConsumed()), this);
	}

	@Override
	public void onMessage(MapRecord<String, String, String> record) {
		log.info("[fanout/group-b] received id={} body={}", record.getId(), record.getValue());
	}
}
