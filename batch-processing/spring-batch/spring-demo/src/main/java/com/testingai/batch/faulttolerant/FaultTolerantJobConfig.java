package com.testingai.batch.faulttolerant;

import javax.sql.DataSource;

import com.testingai.batch.chunk.InvoiceItemWriter;
import com.testingai.batch.domain.Invoice;
import com.testingai.batch.domain.Order;
import com.testingai.batch.domain.OrderRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class FaultTolerantJobConfig {

	private final DataSource dataSource;
	private final FaultTolerantProcessor faultTolerantProcessor;
	private final InvoiceItemWriter invoiceItemWriter;

	@Bean
	public JdbcCursorItemReader<Order> faultTolerantOrderReader() {
		return new JdbcCursorItemReaderBuilder<Order>().name("faultTolerantOrderReader").dataSource(dataSource).sql(
				"SELECT id, batch_type, customer_id, amount, status, created_at FROM orders WHERE batch_type = 'FAULT_TOLERANT' AND status = 'PENDING' ORDER BY id")
				.rowMapper(new OrderRowMapper()).build();
	}

	@Bean
	public Step faultTolerantStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new StepBuilder("faultTolerantStep", jobRepository).<Order, Invoice>chunk(10, transactionManager)
				.reader(faultTolerantOrderReader()).processor(faultTolerantProcessor).writer(invoiceItemWriter)
				.faultTolerant().skip(RuntimeException.class).skipLimit(50).retry(RuntimeException.class).retryLimit(3)
				.build();
	}

	@Bean
	public Job faultTolerantJob(JobRepository jobRepository, Step faultTolerantStep) {
		return new JobBuilder("faultTolerantJob", jobRepository).start(faultTolerantStep).build();
	}
}
