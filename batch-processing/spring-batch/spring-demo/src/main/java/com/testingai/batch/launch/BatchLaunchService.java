package com.testingai.batch.launch;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BatchLaunchService {

	private final JobLauncher jobLauncher;

	public JobRunResult launch(Job job, JobParameters jobParameters) throws JobExecutionAlreadyRunningException,
			JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		JobExecution jobExecution = jobLauncher.run(job, jobParameters);

		int readCount = 0;
		int writeCount = 0;
		int skipCount = 0;
		for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
			readCount += stepExecution.getReadCount();
			writeCount += stepExecution.getWriteCount();
			skipCount += stepExecution.getSkipCount();
		}

		long durationMillis = 0;
		if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
			durationMillis = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
		}

		return new JobRunResult(jobExecution.getId(), jobExecution.getJobInstance().getJobName(),
				jobExecution.getStatus().name(), readCount, writeCount, skipCount, durationMillis);
	}
}
