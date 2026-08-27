package com.testingai.batch.restart;

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
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {BatchTestConfig.class, RestartJobConfig.class, RestartProcessor.class,
		RestartFailureTracker.class, InvoiceItemWriter.class})
@SpringBatchTest
class RestartJobConfigTest {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Job restartDemoJob;

	@BeforeEach
	void setUp() {
		jobLauncherTestUtils.setJob(restartDemoJob);
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM orders");
		for (int i = 0; i < 6; i++) {
			jdbcTemplate.update(
					"INSERT INTO orders (batch_type, customer_id, amount, status) VALUES (?, ?, ?, 'PENDING')",
					BatchType.RESTART.name(), "cust-" + i, BigDecimal.valueOf(50));
		}
	}

	@Test
	void restartDemoJob_shouldFailThenResumeFromLastCommittedChunk() throws Exception {
		String runId = "test-restart-" + System.currentTimeMillis();

		JobExecution firstExecution = jobLauncherTestUtils
				.launchJob(new JobParametersBuilder().addString("runId", runId).toJobParameters());
		assertThat(firstExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

		Integer invoicesAfterFailure = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoices", Integer.class);
		assertThat(invoicesAfterFailure).isEqualTo(3);

		JobExecution secondExecution = jobLauncherTestUtils
				.launchJob(new JobParametersBuilder().addString("runId", runId).toJobParameters());
		assertThat(secondExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		Integer invoicesAfterRestart = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoices", Integer.class);
		assertThat(invoicesAfterRestart).isEqualTo(6);
	}
}
