package com.testingai.batch.partition;

import java.math.BigDecimal;

import com.testingai.batch.BatchTestConfig;
import com.testingai.batch.chunk.InvoiceItemWriter;
import com.testingai.batch.chunk.InvoiceProcessor;
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

@SpringBootTest(classes = {BatchTestConfig.class, PartitionJobConfig.class, OrderRangePartitioner.class,
		InvoiceProcessor.class, InvoiceItemWriter.class})
@SpringBatchTest
class PartitionJobConfigTest {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	void partitionedInvoiceJob_shouldProcessAllOrdersAcrossPartitions() throws Exception {
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(
				new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters());

		assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		Integer invoiceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoices", Integer.class);
		assertThat(invoiceCount).isEqualTo(20);
	}
}
