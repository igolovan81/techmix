package com.testingai.batch.chunk;

import java.math.BigDecimal;

import com.testingai.batch.testsupport.BatchTestConfig;
import com.testingai.batch.domain.BatchType;
import com.testingai.batch.launch.BatchLaunchService;
import com.testingai.batch.launch.JobRunResult;
import com.testingai.batch.listener.InvoiceJobListener;
import com.testingai.batch.listener.InvoiceStepListener;
import com.testingai.batch.listener.ListenerStats;
import com.testingai.batch.listener.ListenerStatsService;
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

@SpringBootTest(classes = {BatchTestConfig.class, ChunkJobConfig.class, InvoiceProcessor.class, InvoiceItemWriter.class,
		InvoiceJobListener.class, InvoiceStepListener.class, ListenerStatsService.class, BatchLaunchService.class})
@SpringBatchTest
class ChunkJobConfigTest {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ListenerStatsService listenerStatsService;

	@Autowired
	private BatchLaunchService batchLaunchService;

	@Autowired
	private Job invoiceChunkJob;

	@BeforeEach
	void setUp() {
		jobLauncherTestUtils.setJob(invoiceChunkJob);
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM orders");
		for (int i = 0; i < 15; i++) {
			jdbcTemplate.update(
					"INSERT INTO orders (batch_type, customer_id, amount, status) VALUES (?, ?, ?, 'PENDING')",
					BatchType.CHUNK.name(), "cust-" + i, BigDecimal.valueOf(100));
		}
	}

	@Test
	void invoiceChunkJob_shouldWriteInvoicesMarkOrdersInvoicedAndRecordListenerStats() throws Exception {
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(
				new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters());

		assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		Integer invoiceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoices", Integer.class);
		assertThat(invoiceCount).isEqualTo(15);

		Integer pendingCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orders WHERE batch_type = 'CHUNK' AND status = 'PENDING'", Integer.class);
		assertThat(pendingCount).isZero();

		ListenerStats stats = listenerStatsService.getLatest();
		assertThat(stats.status()).isEqualTo("COMPLETED");
		assertThat(stats.readCount()).isEqualTo(15);
		assertThat(stats.writeCount()).isEqualTo(15);
	}

	@Test
	void batchLaunchService_shouldAggregateRealJobExecutionIntoJobRunResult() throws Exception {
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM orders");
		for (int i = 0; i < 5; i++) {
			jdbcTemplate.update(
					"INSERT INTO orders (batch_type, customer_id, amount, status) VALUES (?, ?, ?, 'PENDING')",
					BatchType.CHUNK.name(), "cust-" + i, BigDecimal.valueOf(100));
		}

		JobRunResult result = batchLaunchService.launch(invoiceChunkJob,
				new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis() + 1).toJobParameters());

		assertThat(result.jobName()).isEqualTo("invoiceChunkJob");
		assertThat(result.status()).isEqualTo("COMPLETED");
		assertThat(result.readCount()).isEqualTo(5);
		assertThat(result.writeCount()).isEqualTo(5);
		assertThat(result.skipCount()).isZero();
	}
}
