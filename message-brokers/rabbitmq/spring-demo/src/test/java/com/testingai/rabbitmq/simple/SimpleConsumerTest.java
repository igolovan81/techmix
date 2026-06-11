package com.testingai.rabbitmq.simple;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleConsumerTest {

    @InjectMocks
    private SimpleConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void receive_shouldAckOnSuccess() throws Exception {
        consumer.receive("hello", channel, 1L);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void receive_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(1L, false);
        consumer.receive("hello", channel, 1L);
        verify(channel).basicNack(1L, false, true);
    }
}
