package com.testingai.rabbitmq.workqueue;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkQueueConsumerTest {

    @InjectMocks
    private WorkQueueConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void worker1_shouldAckOnSuccess() throws Exception {
        consumer.worker1("task", channel, 1L);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void worker1_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(1L, false);
        consumer.worker1("task", channel, 1L);
        verify(channel).basicNack(1L, false, true);
    }

    @Test
    void worker2_shouldAckOnSuccess() throws Exception {
        consumer.worker2("task", channel, 2L);
        verify(channel).basicAck(2L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void worker2_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(2L, false);
        consumer.worker2("task", channel, 2L);
        verify(channel).basicNack(2L, false, true);
    }
}
