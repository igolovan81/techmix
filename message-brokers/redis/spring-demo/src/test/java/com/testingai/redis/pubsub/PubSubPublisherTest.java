package com.testingai.redis.pubsub;

import com.testingai.redis.config.StreamKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PubSubPublisherTest {
    @Mock RedisTemplate<String, String> redisTemplate;
    @InjectMocks PubSubPublisher publisher;

    @Test
    void publishSendsToChannel() {
        publisher.publish("hello");
        verify(redisTemplate).convertAndSend(StreamKeys.PUBSUB_CHANNEL, "hello");
    }
}
