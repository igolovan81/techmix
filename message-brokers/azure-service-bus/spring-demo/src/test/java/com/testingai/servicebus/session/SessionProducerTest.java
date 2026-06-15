package com.testingai.servicebus.session;

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
class SessionProducerTest {

    @Mock private ServiceBusClientBuilder clientBuilder;
    @Mock private ServiceBusClientBuilder.ServiceBusSenderClientBuilder senderClientBuilder;
    @Mock private ServiceBusSenderClient senderClient;

    private SessionProducer producer;

    @BeforeEach
    void setUp() {
        when(clientBuilder.sender()).thenReturn(senderClientBuilder);
        when(senderClientBuilder.queueName(EntityNames.SESSION_QUEUE)).thenReturn(senderClientBuilder);
        when(senderClientBuilder.buildClient()).thenReturn(senderClient);
        producer = new SessionProducer(clientBuilder);
    }

    @Test
    void send_shouldSetSessionIdOnMessage() {
        producer.send("hello", "session-42");

        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(senderClient).sendMessage(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo("session-42");
        assertThat(captor.getValue().getBody().toString()).isEqualTo("hello");
    }
}
