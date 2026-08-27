package com.testingai.batch.listener;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceJobListener implements JobExecutionListener {

	private final ListenerStatsService listenerStatsService;

	@Override
	public void afterJob(JobExecution jobExecution) {
		int readCount = 0;
		int writeCount = 0;
		int skipCount = 0;
		for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
			// See BatchLaunchService.launch() for why partition-worker step executions (named
			// "<workerStep>:partitionN") are excluded here: their totals are already aggregated
			// into the manager step's own StepExecution, so counting both double-counts.
			if (stepExecution.getStepName().contains(":")) {
				continue;
			}
			readCount += stepExecution.getReadCount();
			writeCount += stepExecution.getWriteCount();
			skipCount += stepExecution.getSkipCount();
		}

		long durationMillis = 0;
		if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
			durationMillis = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
		}

		listenerStatsService.record(new ListenerStats(jobExecution.getJobInstance().getJobName(),
				jobExecution.getStatus().name(), jobExecution.getStartTime(), jobExecution.getEndTime(), durationMillis,
				readCount, writeCount, skipCount));
	}
}
