package com.testingai.redis.workqueue;

import com.testingai.redis.config.StreamKeys;
import com.testingai.redis.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

@Slf4j
public class WorkQueueConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    private final RedisTemplate<String, String> redisTemplate;

    public WorkQueueConsumer(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void registerWith(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        container.receive(
                Consumer.from("work-group", "worker-1"),
                StreamOffset.create(StreamKeys.WORK, ReadOffset.lastConsumed()),
                this);
        container.receive(
                Consumer.from("work-group", "worker-2"),
                StreamOffset.create(StreamKeys.WORK, ReadOffset.lastConsumed()),
                this);
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            FailureSimulator.maybeThrow("work-queue");
            log.info("[work-queue] processed id={} body={}", record.getId(), record.getValue());
            redisTemplate.opsForStream()
                    .acknowledge(StreamKeys.WORK, "work-group", record.getId());
        } catch (RuntimeException e) {
            log.warn("[work-queue] simulated failure id={} — left in PEL", record.getId());
        }
    }
}
