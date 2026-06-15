package com.testingai.servicebus.dlq;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.testingai.servicebus.config.EntityNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqProducerTest {

    @Mock private ServiceBusClientBuilder clientBuilder;
    @Mock private ServiceBusClientBuilder.ServiceBusSenderClientBuilder senderClientBuilder;
    @Mock private ServiceBusSenderClient senderClient;

    private DlqProducer producer;

    @BeforeEach
    void setUp() {
        when(clientBuilder.sender()).thenReturn(senderClientBuilder);
        when(senderClientBuilder.queueName(EntityNames.DLQ_QUEUE)).thenReturn(senderClientBuilder);
        when(senderClientBuilder.buildClient()).thenReturn(senderClient);
        producer = new DlqProducer(clientBuilder);
    }

    @Test
    void send_shouldSendMessageToDlqQueue() {
        producer.send("risky-message");

        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(senderClient).sendMessage(captor.capture());
        assertThat(captor.getValue().getBody().toString()).isEqualTo("risky-message");
    }
}
