package com.testingai.rabbitmq.routing;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingConsumerTest {

    @InjectMocks
    private RoutingConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void receiveAll_shouldAckOnSuccess() throws Exception {
        consumer.receiveAll("info message", channel, 1L);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void receiveAll_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(1L, false);
        consumer.receiveAll("info message", channel, 1L);
        verify(channel).basicNack(1L, false, true);
    }

    @Test
    void receiveError_shouldAckOnSuccess() throws Exception {
        consumer.receiveError("error message", channel, 2L);
        verify(channel).basicAck(2L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void receiveError_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(2L, false);
        consumer.receiveError("error message", channel, 2L);
        verify(channel).basicNack(2L, false, true);
    }
}
