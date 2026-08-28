package com.testingai.batch.chunk;

import javax.sql.DataSource;

import com.testingai.batch.domain.Invoice;
import com.testingai.batch.domain.Order;
import com.testingai.batch.domain.OrderRowMapper;
import com.testingai.batch.listener.InvoiceJobListener;
import com.testingai.batch.listener.InvoiceStepListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
public class ChunkJobConfig {

	private final DataSource dataSource;
	private final InvoiceProcessor invoiceProcessor;
	private final InvoiceItemWriter invoiceItemWriter;
	private final InvoiceJobListener invoiceJobListener;
	private final InvoiceStepListener invoiceStepListener;

	/**
	 * @StepScope is required, not optional: JdbcCursorItemReader holds a stateful, open cursor. As a plain
	 *            singleton @Bean it would be shared across every concurrent execution of this job, and concurrent reads
	 *            against one shared cursor corrupt each other's position ("Unexpected cursor position
	 *            change"). @StepScope gives each step execution its own instance.
	 */
	@Bean
	@StepScope
	public JdbcCursorItemReader<Order> chunkOrderReader() {
		return new JdbcCursorItemReaderBuilder<Order>().name("chunkOrderReader").dataSource(dataSource).sql(
				"SELECT id, batch_type, customer_id, amount, status, created_at FROM orders WHERE batch_type = 'CHUNK' AND status = 'PENDING' ORDER BY id")
				.rowMapper(new OrderRowMapper()).build();
	}

	@Bean
	public Step invoiceChunkStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new StepBuilder("invoiceChunkStep", jobRepository).<Order, Invoice>chunk(10, transactionManager)
				.reader(chunkOrderReader()).processor(invoiceProcessor).writer(invoiceItemWriter)
				.listener(invoiceStepListener).build();
	}

	@Bean
	public Job invoiceChunkJob(JobRepository jobRepository, Step invoiceChunkStep) {
		return new JobBuilder("invoiceChunkJob", jobRepository).start(invoiceChunkStep).listener(invoiceJobListener)
				.build();
	}
}
