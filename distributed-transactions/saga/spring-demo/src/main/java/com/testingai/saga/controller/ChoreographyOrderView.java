package com.testingai.saga.controller;

import com.testingai.saga.choreography.SagaLogEntry;
import com.testingai.saga.domain.SagaStatus;

import java.util.List;

public record ChoreographyOrderView(String orderId, SagaStatus status, List<SagaLogEntry> timeline) {
}
