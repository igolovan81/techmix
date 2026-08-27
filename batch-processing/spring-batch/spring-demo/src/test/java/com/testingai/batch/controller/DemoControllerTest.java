package com.testingai.batch.controller;

import java.util.List;

import com.testingai.batch.domain.Invoice;
import com.testingai.batch.launch.BatchLaunchService;
import com.testingai.batch.launch.JobRunResult;
import com.testingai.batch.listener.ListenerStatsService;
import com.testingai.batch.seed.OrderSeedService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OrderSeedService orderSeedService;
	@MockitoBean
	private BatchLaunchService batchLaunchService;
	@MockitoBean
	private ListenerStatsService listenerStatsService;
	@MockitoBean
	private JdbcTemplate jdbcTemplate;
	@MockitoBean(name = "invoiceChunkJob")
	private Job invoiceChunkJob;
	@MockitoBean(name = "archiveSummaryJob")
	private Job archiveSummaryJob;
	@MockitoBean(name = "faultTolerantJob")
	private Job faultTolerantJob;
	@MockitoBean(name = "restartDemoJob")
	private Job restartDemoJob;
	@MockitoBean(name = "partitionedInvoiceJob")
	private Job partitionedInvoiceJob;

	@Test
	void seedOrders_shouldReturn200AndDelegate() throws Exception {
		when(orderSeedService.seed(any(), eq(10))).thenReturn(10);

		mockMvc.perform(post("/demo/orders/seed").param("type", "CHUNK").param("count", "10"))
				.andExpect(status().isOk());

		verify(orderSeedService).seed(any(), eq(10));
	}

	@Test
	void launchChunk_shouldReturn200AndDelegate() throws Exception {
		when(batchLaunchService.launch(eq(invoiceChunkJob), any()))
				.thenReturn(new JobRunResult(1L, "invoiceChunkJob", "COMPLETED", 5, 5, 0, 100L));

		mockMvc.perform(post("/demo/batch/chunk")).andExpect(status().isOk());

		verify(batchLaunchService).launch(eq(invoiceChunkJob), any());
	}

	@Test
	void listenerStats_shouldReturn200AndDelegate() throws Exception {
		when(listenerStatsService.getLatest()).thenReturn(null);

		mockMvc.perform(get("/demo/batch/listener-stats")).andExpect(status().isOk());

		verify(listenerStatsService).getLatest();
	}

	@Test
	void launchTasklet_shouldReturn200AndDelegate() throws Exception {
		when(batchLaunchService.launch(eq(archiveSummaryJob), any()))
				.thenReturn(new JobRunResult(2L, "archiveSummaryJob", "COMPLETED", 0, 0, 0, 10L));

		mockMvc.perform(post("/demo/batch/tasklet")).andExpect(status().isOk());

		verify(batchLaunchService).launch(eq(archiveSummaryJob), any());
	}

	@Test
	void launchFaultTolerant_shouldReturn200AndDelegate() throws Exception {
		when(batchLaunchService.launch(eq(faultTolerantJob), any()))
				.thenReturn(new JobRunResult(3L, "faultTolerantJob", "COMPLETED", 100, 99, 1, 500L));

		mockMvc.perform(post("/demo/batch/fault-tolerant")).andExpect(status().isOk());

		verify(batchLaunchService).launch(eq(faultTolerantJob), any());
	}

	@Test
	void launchRestartDemo_shouldReturn200AndDelegate() throws Exception {
		when(batchLaunchService.launch(eq(restartDemoJob), any()))
				.thenReturn(new JobRunResult(4L, "restartDemoJob", "FAILED", 5, 3, 0, 200L));

		mockMvc.perform(post("/demo/batch/restart-demo").param("runId", "demo-1")).andExpect(status().isOk());

		verify(batchLaunchService).launch(eq(restartDemoJob), any());
	}

	@Test
	void launchPartition_shouldReturn200AndDelegate() throws Exception {
		when(batchLaunchService.launch(eq(partitionedInvoiceJob), any()))
				.thenReturn(new JobRunResult(5L, "partitionedInvoiceJob", "COMPLETED", 20, 20, 0, 300L));

		mockMvc.perform(post("/demo/batch/partition")).andExpect(status().isOk());

		verify(batchLaunchService).launch(eq(partitionedInvoiceJob), any());
	}

	@SuppressWarnings("unchecked")
	@Test
	void invoices_shouldReturn200AndDelegate() throws Exception {
		when(jdbcTemplate.query(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(List.of());

		mockMvc.perform(get("/demo/invoices")).andExpect(status().isOk());
	}
}
