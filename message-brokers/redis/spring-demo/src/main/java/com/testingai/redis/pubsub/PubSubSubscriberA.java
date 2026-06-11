package com.testingai.redis.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PubSubSubscriberA implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = message != null ? new String(message.getBody()) : "(null)";
        log.info("[pubsub/subscriber-a] received: {}", body);
    }
}
