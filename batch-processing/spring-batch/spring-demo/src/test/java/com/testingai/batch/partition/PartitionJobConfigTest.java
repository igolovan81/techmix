package com.testingai.batch.partition;

import java.math.BigDecimal;

import com.testingai.batch.chunk.InvoiceItemWriter;
import com.testingai.batch.chunk.InvoiceProcessor;
import com.testingai.batch.domain.BatchType;
import com.testingai.batch.launch.BatchLaunchService;
import com.testingai.batch.launch.JobRunResult;
import com.testingai.batch.testsupport.BatchTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {BatchTestConfig.class, PartitionJobConfig.class, OrderRangePartitioner.class,
		InvoiceProcessor.class, InvoiceItemWriter.class, BatchLaunchService.class})
@SpringBatchTest
class PartitionJobConfigTest {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BatchLaunchService batchLaunchService;

	@Autowired
	private Job partitionedInvoiceJob;

	@BeforeEach
	void setUp() {
		jobLauncherTestUtils.setJob(partitionedInvoiceJob);
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM orders");
		for (int i = 0; i < 20; i++) {
			jdbcTemplate.update(
					"INSERT INTO orders (batch_type, customer_id, amount, status) VALUES (?, ?, ?, 'PENDING')",
					BatchType.PARTITION.name(), "cust-" + i, BigDecimal.valueOf(75));
		}
	}

	@Test
	void partitionedInvoiceJob_shouldProcessAllOrdersAcrossPartitionsWithoutDoubleCountingStats() throws Exception {
		JobRunResult result = batchLaunchService.launch(partitionedInvoiceJob,
				new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters());

		assertThat(result.status()).isEqualTo("COMPLETED");
		// Guards against double-counting: the manager step's own StepExecution already aggregates
		// the 4 worker partitions' totals, so a naive sum over every StepExecution would report 40
		// (double the true 20) here.
		assertThat(result.readCount()).isEqualTo(20);
		assertThat(result.writeCount()).isEqualTo(20);

		Integer invoiceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoices", Integer.class);
		assertThat(invoiceCount).isEqualTo(20);
	}
}
