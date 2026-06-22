package com.testingai.axon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.axon.command.AddOrderLineCommand;
import com.testingai.axon.command.CancelOrderCommand;
import com.testingai.axon.command.ConfirmOrderCommand;
import com.testingai.axon.command.CreateOrderCommand;
import com.testingai.axon.query.FindAllOrdersQuery;
import com.testingai.axon.query.FindOrderQuery;
import com.testingai.axon.query.OrderSummary;
import com.testingai.axon.replay.ReplayService;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CommandGateway commandGateway;
	@MockitoBean
	private QueryGateway queryGateway;
	@MockitoBean
	private ReplayService replayService;

	@Test
	void createOrder_shouldReturn200AndDispatchCommand() throws Exception {
		mockMvc.perform(post("/demo/orders").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateOrderRequest("customer-1"))))
				.andExpect(status().isOk());

		verify(commandGateway).sendAndWait(any(CreateOrderCommand.class));
	}

	@Test
	void addLine_shouldReturn200AndDispatchCommand() throws Exception {
		mockMvc.perform(post("/demo/orders/order-1/lines").contentType("application/json")
				.content(objectMapper.writeValueAsString(new AddOrderLineRequest("product-1", 2, BigDecimal.TEN))))
				.andExpect(status().isOk());

		verify(commandGateway).sendAndWait(new AddOrderLineCommand("order-1", "product-1", 2, BigDecimal.TEN));
	}

	@Test
	void confirmOrder_shouldReturn200AndDispatchCommand() throws Exception {
		mockMvc.perform(post("/demo/orders/order-1/confirm")).andExpect(status().isOk());

		verify(commandGateway).sendAndWait(new ConfirmOrderCommand("order-1"));
	}

	@Test
	void confirmOrder_whenAlreadyConfirmed_shouldReturn409() throws Exception {
		when(commandGateway.sendAndWait(new ConfirmOrderCommand("order-1")))
				.thenThrow(new CommandExecutionException("failed", new IllegalStateException("already confirmed")));

		mockMvc.perform(post("/demo/orders/order-1/confirm")).andExpect(status().isConflict());
	}

	@Test
	void cancelOrder_shouldReturn200AndDispatchCommand() throws Exception {
		mockMvc.perform(post("/demo/orders/order-1/cancel")).andExpect(status().isOk());

		verify(commandGateway).sendAndWait(new CancelOrderCommand("order-1"));
	}

	@Test
	void getOrder_shouldReturn200WhenFound() throws Exception {
		OrderSummary summary = new OrderSummary("order-1", "customer-1", 1, "CREATED");
		when(queryGateway.query(eq(new FindOrderQuery("order-1")), eq(OrderSummary.class)))
				.thenReturn(CompletableFuture.completedFuture(summary));

		mockMvc.perform(get("/demo/orders/order-1")).andExpect(status().isOk());
	}

	@Test
	void getOrder_shouldReturn404WhenMissing() throws Exception {
		when(queryGateway.query(eq(new FindOrderQuery("missing")), eq(OrderSummary.class)))
				.thenReturn(CompletableFuture.completedFuture(null));

		mockMvc.perform(get("/demo/orders/missing")).andExpect(status().isNotFound());
	}

	@Test
	void getAllOrders_shouldReturn200() throws Exception {
		when(queryGateway.query(eq(new FindAllOrdersQuery()), eq(ResponseTypes.multipleInstancesOf(OrderSummary.class))))
				.thenReturn(CompletableFuture.completedFuture(List.of()));

		mockMvc.perform(get("/demo/orders")).andExpect(status().isOk());
	}

	@Test
	void replay_shouldReturn202AndDelegate() throws Exception {
		mockMvc.perform(post("/demo/orders/replay")).andExpect(status().isAccepted());

		verify(replayService).replayOrderProjection();
	}
}
