package com.testingai.servicebus.transactions;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.ServiceBusTransactionContext;
import com.testingai.servicebus.config.EntityNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionalProducerTest {

    @Mock private ServiceBusClientBuilder clientBuilder;
    @Mock private ServiceBusClientBuilder.ServiceBusSenderClientBuilder senderClientBuilder;
    @Mock private ServiceBusSenderClient senderClient;
    @Mock private ServiceBusTransactionContext txContext;

    private TransactionalProducer producer;

    @BeforeEach
    void setUp() {
        when(clientBuilder.sender()).thenReturn(senderClientBuilder);
        when(senderClientBuilder.queueName(EntityNames.TX_QUEUE)).thenReturn(senderClientBuilder);
        when(senderClientBuilder.buildClient()).thenReturn(senderClient);
        when(senderClient.createTransaction()).thenReturn(txContext);
        producer = new TransactionalProducer(clientBuilder);
    }

    @Test
    void send_shouldCommitAllMessagesInOneTransaction() {
        producer.send("hello", 3);

        verify(senderClient).createTransaction();
        verify(senderClient, times(3)).sendMessage(any(ServiceBusMessage.class), eq(txContext));
        verify(senderClient).commitTransaction(txContext);
        verify(senderClient, never()).rollbackTransaction(any());
    }
}
