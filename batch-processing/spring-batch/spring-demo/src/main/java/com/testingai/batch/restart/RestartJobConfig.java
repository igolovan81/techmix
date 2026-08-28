package com.testingai.batch.restart;

import javax.sql.DataSource;

import com.testingai.batch.chunk.InvoiceItemWriter;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class RestartJobConfig {

	private final DataSource dataSource;
	private final RestartProcessor restartProcessor;
	private final InvoiceItemWriter invoiceItemWriter;

	/**
	 * Unlike every other reader in this module, this query is NOT filtered by status = 'PENDING'.
	 * JdbcCursorItemReader's restart mechanism resumes by re-running this exact query and skipping forward N rows (N =
	 * however many it had already read at the last successful commit) -- that only works if the query returns the same
	 * rows in the same order across restarts. Filtering by status would shrink the result set after the writer flips
	 * committed rows to INVOICED, silently skipping past unprocessed rows on restart instead of resuming at the right
	 * one.
	 *
	 * @StepScope is also required here for the usual reason (see ChunkJobConfig/FaultTolerantJobConfig): a plain
	 *            singleton @Bean would share one stateful cursor across every concurrent execution of this job,
	 *            corrupting cursor position under concurrent requests.
	 */
	@Bean
	@StepScope
	public JdbcCursorItemReader<Order> restartOrderReader() {
		return new JdbcCursorItemReaderBuilder<Order>().name("restartOrderReader").dataSource(dataSource).sql(
				"SELECT id, batch_type, customer_id, amount, status, created_at FROM orders WHERE batch_type = 'RESTART' ORDER BY id")
				.rowMapper(new OrderRowMapper()).saveState(true).build();
	}

	@Bean
	public Step restartDemoStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new StepBuilder("restartDemoStep", jobRepository).<Order, Invoice>chunk(3, transactionManager)
				.reader(restartOrderReader()).processor(restartProcessor).writer(invoiceItemWriter).build();
	}

	@Bean
	public Job restartDemoJob(JobRepository jobRepository, Step restartDemoStep) {
		return new JobBuilder("restartDemoJob", jobRepository).start(restartDemoStep).build();
	}
}
