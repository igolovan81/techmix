package com.testingai.redis.trimming;

import com.testingai.redis.config.StreamKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

@Slf4j
public class TrimmingConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    private final RedisTemplate<String, String> redisTemplate;

    public TrimmingConsumer(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void registerWith(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        container.receive(
                Consumer.from("trimmed-group", "trimmer-1"),
                StreamOffset.create(StreamKeys.TRIMMED, ReadOffset.lastConsumed()),
                this);
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        log.info("[trimming] received id={} body={}", record.getId(), record.getValue());
        redisTemplate.opsForStream()
                .acknowledge(StreamKeys.TRIMMED, "trimmed-group", record.getId());
        Long length = redisTemplate.opsForStream().size(StreamKeys.TRIMMED);
        log.info("[trimming] stream length after ack: {}", length);
    }
}
