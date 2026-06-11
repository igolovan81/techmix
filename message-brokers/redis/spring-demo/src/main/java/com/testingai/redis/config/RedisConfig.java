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

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
public class RedisConfig {

    private final RedisConnectionFactory connectionFactory;
    private final RedisTemplate<String, String> redisTemplate;

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
                .builder()
                .pollTimeout(Duration.ofMillis(100))
                .build();
        var container = StreamMessageListenerContainer
                .create(connectionFactory, options);
        container.start();
        return container;
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
                if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                    log.debug("Consumer group '{}' on '{}' already exists", group, stream);
                } else {
                    throw e;
                }
            }
        }
    }
}
