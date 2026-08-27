package com.testingai.batch.controller;

import java.util.List;
import java.util.Map;

import com.testingai.batch.domain.BatchType;
import com.testingai.batch.domain.Invoice;
import com.testingai.batch.launch.BatchLaunchService;
import com.testingai.batch.launch.JobRunResult;
import com.testingai.batch.listener.ListenerStats;
import com.testingai.batch.listener.ListenerStatsService;
import com.testingai.batch.seed.OrderSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

	private final OrderSeedService orderSeedService;
	private final BatchLaunchService batchLaunchService;
	private final ListenerStatsService listenerStatsService;
	private final JdbcTemplate jdbcTemplate;
	private final Job invoiceChunkJob;
	private final Job archiveSummaryJob;
	private final Job faultTolerantJob;
	private final Job restartDemoJob;
	private final Job partitionedInvoiceJob;

	@PostMapping("/orders/seed")
	public Map<String, Integer> seedOrders(@RequestParam BatchType type, @RequestParam int count) {
		return Map.of("seeded", orderSeedService.seed(type, count));
	}

	@PostMapping("/batch/chunk")
	public JobRunResult launchChunk() throws JobExecutionAlreadyRunningException, JobRestartException,
			JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		return batchLaunchService.launch(invoiceChunkJob, uniqueParameters());
	}

	@GetMapping("/batch/listener-stats")
	public ListenerStats listenerStats() {
		return listenerStatsService.getLatest();
	}

	@PostMapping("/batch/tasklet")
	public JobRunResult launchTasklet() throws JobExecutionAlreadyRunningException, JobRestartException,
			JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		return batchLaunchService.launch(archiveSummaryJob, uniqueParameters());
	}

	@PostMapping("/batch/fault-tolerant")
	public JobRunResult launchFaultTolerant() throws JobExecutionAlreadyRunningException, JobRestartException,
			JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		return batchLaunchService.launch(faultTolerantJob, uniqueParameters());
	}

	@PostMapping("/batch/restart-demo")
	public JobRunResult launchRestartDemo(@RequestParam String runId) throws JobExecutionAlreadyRunningException,
			JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		return batchLaunchService.launch(restartDemoJob,
				new JobParametersBuilder().addString("runId", runId).toJobParameters());
	}

	@PostMapping("/batch/partition")
	public JobRunResult launchPartition() throws JobExecutionAlreadyRunningException, JobRestartException,
			JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		return batchLaunchService.launch(partitionedInvoiceJob, uniqueParameters());
	}

	@GetMapping("/invoices")
	public List<Invoice> invoices() {
		return jdbcTemplate.query("SELECT * FROM invoices ORDER BY id", new BeanPropertyRowMapper<>(Invoice.class));
	}

	private JobParameters uniqueParameters() {
		return new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters();
	}
}
