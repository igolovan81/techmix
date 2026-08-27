package com.testingai.batch.tasklet;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class TaskletJobConfig {

	private final ArchiveSummaryTasklet archiveSummaryTasklet;

	@Bean
	public Step archiveSummaryStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new StepBuilder("archiveSummaryStep", jobRepository).tasklet(archiveSummaryTasklet, transactionManager)
				.build();
	}

	@Bean
	public Job archiveSummaryJob(JobRepository jobRepository, Step archiveSummaryStep) {
		return new JobBuilder("archiveSummaryJob", jobRepository).start(archiveSummaryStep).build();
	}
}
