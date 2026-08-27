package com.testingai.batch.faulttolerant;

import java.math.BigDecimal;

import com.testingai.batch.BatchTestConfig;
import com.testingai.batch.chunk.InvoiceItemWriter;
import com.testingai.batch.domain.BatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {BatchTestConfig.class, FaultTolerantJobConfig.class, FaultTolerantProcessor.class,
		InvoiceItemWriter.class})
@SpringBatchTest
class FaultTolerantJobConfigTest {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Job faultTolerantJob;

	@BeforeEach
	void setUp() {
		jobLauncherTestUtils.setJob(faultTolerantJob);
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM orders");
		for (int i = 0; i < 100; i++) {
			jdbcTemplate.update(
					"INSERT INTO orders (batch_type, customer_id, amount, status) VALUES (?, ?, ?, 'PENDING')",
					BatchType.FAULT_TOLERANT.name(), "cust-" + i, BigDecimal.valueOf(50));
		}
	}

	@Test
	void faultTolerantJob_shouldCompleteAndAccountForEveryItem() throws Exception {
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(
				new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters());

		assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		long totalAccounted = jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum()
				+ jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getSkipCount).sum();
		assertThat(totalAccounted).isEqualTo(100);
	}
}
