package com.testingai.batch.partition;

import javax.sql.DataSource;

import com.testingai.batch.chunk.InvoiceItemWriter;
import com.testingai.batch.chunk.InvoiceProcessor;
import com.testingai.batch.domain.Invoice;
import com.testingai.batch.domain.Order;
import com.testingai.batch.domain.OrderRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class PartitionJobConfig {

	private static final int GRID_SIZE = 4;

	private final DataSource dataSource;
	private final OrderRangePartitioner orderRangePartitioner;
	private final InvoiceProcessor invoiceProcessor;
	private final InvoiceItemWriter invoiceItemWriter;

	@Bean
	@StepScope
	public JdbcCursorItemReader<Order> partitionOrderReader(@Value("#{stepExecutionContext['minId']}") Long minId,
			@Value("#{stepExecutionContext['maxId']}") Long maxId) {
		return new JdbcCursorItemReaderBuilder<Order>().name("partitionOrderReader").dataSource(dataSource).sql(
				"SELECT id, batch_type, customer_id, amount, status, created_at FROM orders WHERE batch_type = 'PARTITION' AND status = 'PENDING' AND id BETWEEN ? AND ? ORDER BY id")
				.preparedStatementSetter(ps -> {
					ps.setLong(1, minId);
					ps.setLong(2, maxId);
				}).rowMapper(new OrderRowMapper()).build();
	}

	@Bean
	public Step partitionWorkerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			JdbcCursorItemReader<Order> partitionOrderReader) {
		return new StepBuilder("partitionWorkerStep", jobRepository).<Order, Invoice>chunk(10, transactionManager)
				.reader(partitionOrderReader).processor(invoiceProcessor).writer(invoiceItemWriter).build();
	}

	@Bean
	public Step partitionedInvoiceStep(JobRepository jobRepository, Step partitionWorkerStep) {
		return new StepBuilder("partitionedInvoiceStep", jobRepository)
				.partitioner("partitionWorkerStep", orderRangePartitioner).step(partitionWorkerStep).gridSize(GRID_SIZE)
				.taskExecutor(new SimpleAsyncTaskExecutor()).build();
	}

	@Bean
	public Job partitionedInvoiceJob(JobRepository jobRepository, Step partitionedInvoiceStep) {
		return new JobBuilder("partitionedInvoiceJob", jobRepository).start(partitionedInvoiceStep).build();
	}
}
