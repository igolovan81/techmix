package com.testingai.redis.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import com.testingai.redis.fanout.FanoutConsumerA;
import com.testingai.redis.fanout.FanoutConsumerB;
import com.testingai.redis.pending.PendingConsumer;
import com.testingai.redis.simple.SimpleConsumer;
import com.testingai.redis.trimming.TrimmingConsumer;
import com.testingai.redis.workqueue.WorkQueueConsumer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
public class RedisConfig {

    private final RedisConnectionFactory connectionFactory;
    private final RedisTemplate<String, String> redisTemplate;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer;

    public RedisConfig(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        this.redisTemplate = buildTemplate(connectionFactory);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate() {
        return redisTemplate;
    }

    private RedisTemplate<String, String> buildTemplate(RedisConnectionFactory cf) {
        var tpl = new RedisTemplate<String, String>();
        tpl.setConnectionFactory(cf);
        tpl.setKeySerializer(new StringRedisSerializer());
        tpl.setValueSerializer(new StringRedisSerializer());
        tpl.setHashKeySerializer(new StringRedisSerializer());
        tpl.setHashValueSerializer(new StringRedisSerializer());
        tpl.afterPropertiesSet();
        return tpl;
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
            streamListenerContainer() {
        var options = StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofMillis(100))
                .serializer(new StringRedisSerializer())
                .build();
        var container = StreamMessageListenerContainer
                .create(connectionFactory, options);
        this.streamContainer = container;
        return container;
    }

    @Bean
    public SimpleConsumer simpleConsumer(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        return new SimpleConsumer(container);
    }

    @Bean
    public WorkQueueConsumer workQueueConsumer(
            RedisTemplate<String, String> redisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        var consumer = new WorkQueueConsumer(redisTemplate);
        consumer.registerWith(container);
        return consumer;
    }

    @Bean
    public PendingConsumer pendingConsumer(
            RedisTemplate<String, String> redisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        var consumer = new PendingConsumer(redisTemplate);
        consumer.registerWith(container);
        return consumer;
    }

    @Bean
    public FanoutConsumerA fanoutConsumerA(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        return new FanoutConsumerA(container);
    }

    @Bean
    public FanoutConsumerB fanoutConsumerB(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        return new FanoutConsumerB(container);
    }

    @Bean
    public TrimmingConsumer trimmingConsumer(
            RedisTemplate<String, String> redisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        var consumer = new TrimmingConsumer(redisTemplate);
        consumer.registerWith(container);
        return consumer;
    }

    @Bean
    public RedisMessageListenerContainer pubSubListenerContainer() {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /** Bootstrap consumer groups for all stream patterns. */
    @PostConstruct
    public void createConsumerGroups() {
        List<String[]> streamGroups = List.of(
                new String[]{StreamKeys.WORK,    "work-group"},
                new String[]{StreamKeys.FANOUT,  "group-a"},
                new String[]{StreamKeys.FANOUT,  "group-b"},
                new String[]{StreamKeys.PENDING, "pending-group"},
                new String[]{StreamKeys.TRIMMED, "trimmed-group"}
        );
        for (String[] sg : streamGroups) {
            String stream = sg[0];
            String group  = sg[1];
            try {
                redisTemplate.opsForStream()
                        .createGroup(stream, ReadOffset.from("$"), group);
                log.info("Created consumer group '{}' on '{}'", group, stream);
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("BUSYGROUP") || msg.contains("ERR"))) {
                    log.debug("Consumer group '{}' on '{}' — skipped: {}", group, stream, msg);
                } else {
                    throw e;
                }
            }
        }
        if (streamContainer != null) {
            streamContainer.start();
        }
    }
}
