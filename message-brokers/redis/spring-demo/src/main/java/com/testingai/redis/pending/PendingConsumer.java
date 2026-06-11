package com.testingai.redis.pending;

import com.testingai.redis.config.StreamKeys;
import com.testingai.redis.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

@Slf4j
public class PendingConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    private static final String GROUP    = "pending-group";
    private static final String CONSUMER = "pending-consumer";
    private static final long   MIN_IDLE = 5000L;

    private final RedisTemplate<String, String> redisTemplate;

    public PendingConsumer(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void registerWith(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        container.receive(
                Consumer.from(GROUP, CONSUMER),
                StreamOffset.create(StreamKeys.PENDING, ReadOffset.lastConsumed()),
                this);
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            process(record);
            redisTemplate.opsForStream()
                    .acknowledge(StreamKeys.PENDING, GROUP, record.getId());
        } catch (RuntimeException e) {
            log.warn("[pending] simulated failure id={} — left in PEL", record.getId());
        }
    }

    private void process(MapRecord<String, String, String> record) {
        FailureSimulator.maybeThrow("pending");
        log.info("[pending] processed id={} body={}", record.getId(), record.getValue());
    }

    @Scheduled(fixedDelay = 3000)
    public void reclaimPending() {
        try {
            PendingMessages messages = redisTemplate.opsForStream().pending(
                    StreamKeys.PENDING,
                    Consumer.from(GROUP, CONSUMER),
                    Range.unbounded(),
                    10L);

            for (PendingMessage pm : messages) {
                if (pm.getElapsedTimeSinceLastDelivery().toMillis() >= MIN_IDLE) {
                    List<MapRecord<String, Object, Object>> claimed =
                            redisTemplate.opsForStream().claim(
                                    StreamKeys.PENDING,
                                    GROUP,
                                    CONSUMER,
                                    Duration.ofMillis(MIN_IDLE),
                                    pm.getId());
                    if (claimed == null || claimed.isEmpty()) continue;
                    for (var rec : claimed) {
                        try {
                            log.info("[pending] reclaiming and reprocessing id={}", rec.getId());
                            FailureSimulator.maybeThrow("pending-reclaim");
                            redisTemplate.opsForStream()
                                    .acknowledge(StreamKeys.PENDING, GROUP, rec.getId());
                            log.info("[pending] reclaimed entry acknowledged id={}", rec.getId());
                        } catch (RuntimeException e) {
                            log.warn("[pending] reclaim attempt failed id={}, will retry in next cycle", rec.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[pending] reclaimer error: {}", e.getMessage());
        }
    }
}
