package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStatus;
import com.testingai.saga.domain.SagaStep;

import java.util.List;

public record SagaResult(String orderId, SagaStatus status, SagaStep failedStep, List<SagaStep> compensatedSteps) {
}
