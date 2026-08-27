package com.testingai.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InvoiceStepListener implements StepExecutionListener {

	@Override
	public void beforeStep(StepExecution stepExecution) {
		log.info("[InvoiceStepListener] Starting step '{}'", stepExecution.getStepName());
	}

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		log.info("[InvoiceStepListener] Step '{}' finished: read={}, write={}, skip={}", stepExecution.getStepName(),
				stepExecution.getReadCount(), stepExecution.getWriteCount(), stepExecution.getSkipCount());
		return stepExecution.getExitStatus();
	}
}
