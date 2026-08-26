package com.testingai.disruptor.controller;

import com.testingai.disruptor.diamond.DiamondResult;
import com.testingai.disruptor.diamond.DiamondService;
import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.errors.ErrorsResult;
import com.testingai.disruptor.errors.ErrorsService;
import com.testingai.disruptor.parallel.ParallelHandlersService;
import com.testingai.disruptor.parallel.ParallelResult;
import com.testingai.disruptor.producer.ProducerComparisonService;
import com.testingai.disruptor.producer.ProducerStat;
import com.testingai.disruptor.single.SingleHandlerResult;
import com.testingai.disruptor.single.SingleHandlerService;
import com.testingai.disruptor.waitstrategy.WaitStrategyComparisonService;
import com.testingai.disruptor.waitstrategy.WaitStrategyStat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SingleHandlerService singleHandlerService;

	@MockitoBean
	private ParallelHandlersService parallelHandlersService;

	@MockitoBean
	private DiamondService diamondService;

	@MockitoBean
	private ProducerComparisonService producerComparisonService;

	@MockitoBean
	private WaitStrategyComparisonService waitStrategyComparisonService;

	@MockitoBean
	private ErrorsService errorsService;

	@Test
	void singleEndpointReturnsResult() throws Exception {
		when(singleHandlerService.process(anyInt())).thenReturn(new SingleHandlerResult(1000, 10, 100000.0));

		mockMvc.perform(post("/demo/disruptor/single").param("eventCount", "1000")).andExpect(status().isOk())
				.andExpect(jsonPath("$.eventsProcessed").value(1000));
	}

	@Test
	void parallelEndpointReturnsResult() throws Exception {
		when(parallelHandlersService.process(anyInt())).thenReturn(new ParallelResult(1000, 1000, 10));

		mockMvc.perform(post("/demo/disruptor/parallel").param("eventCount", "1000")).andExpect(status().isOk())
				.andExpect(jsonPath("$.journalCount").value(1000)).andExpect(jsonPath("$.riskCheckCount").value(1000));
	}

	@Test
	void diamondEndpointReturnsResult() throws Exception {
		Fill fill = new Fill("AAPL", "order-1", "order-2", 10, BigDecimal.TEN);
		when(diamondService.process(anyInt())).thenReturn(new DiamondResult(List.of(fill), 3, 10));

		mockMvc.perform(post("/demo/disruptor/diamond").param("eventCount", "1000")).andExpect(status().isOk())
				.andExpect(jsonPath("$.fills[0].symbol").value("AAPL")).andExpect(jsonPath("$.restingOrders").value(3));
	}

	@Test
	void producerEndpointReturnsComparison() throws Exception {
		when(producerComparisonService.compare(anyInt(), anyInt())).thenReturn(List.of(
				new ProducerStat("SINGLE", 1, 1000, 10, 100000.0), new ProducerStat("MULTI", 4, 1000, 12, 83333.0)));

		mockMvc.perform(post("/demo/disruptor/producer").param("eventCount", "1000").param("threads", "4"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].producerType").value("SINGLE"))
				.andExpect(jsonPath("$[1].producerType").value("MULTI"));
	}

	@Test
	void waitStrategyEndpointReturnsComparison() throws Exception {
		when(waitStrategyComparisonService.compare(anyInt()))
				.thenReturn(List.of(new WaitStrategyStat("BUSY_SPIN", 1000, 5, 200000.0, 2.0),
						new WaitStrategyStat("YIELDING", 1000, 7, 142857.0, 5.0),
						new WaitStrategyStat("BLOCKING", 1000, 10, 100000.0, 10.0)));

		mockMvc.perform(post("/demo/disruptor/waitstrategy").param("eventCount", "10000")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].strategyName").value("BUSY_SPIN"));
	}

	@Test
	void errorsEndpointReturnsResult() throws Exception {
		when(errorsService.process(anyInt())).thenReturn(new ErrorsResult(950, 50, 10));

		mockMvc.perform(post("/demo/disruptor/errors").param("eventCount", "1000")).andExpect(status().isOk())
				.andExpect(jsonPath("$.succeeded").value(950)).andExpect(jsonPath("$.failed").value(50));
	}

	@Test
	void eventCountAboveCapIsRejected() throws Exception {
		mockMvc.perform(post("/demo/disruptor/single").param("eventCount", "100001"))
				.andExpect(status().isBadRequest());
	}
}
