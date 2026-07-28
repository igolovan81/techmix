package com.testingai.grpc.client.controller;

import com.testingai.grpc.client.support.FakeProductCatalogService;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grpc.client.catalog-service.address=in-process:demo-integration-test")
@AutoConfigureMockMvc
class DemoIntegrationTest {

	private static Server inProcessServer;

	@Autowired
	private MockMvc mockMvc;

	@BeforeAll
	static void startFakeServer() throws IOException {
		inProcessServer = InProcessServerBuilder.forName("demo-integration-test").directExecutor()
				.addService(new FakeProductCatalogService()).build().start();
	}

	@AfterAll
	static void stopFakeServer() {
		inProcessServer.shutdownNow();
	}

	@Test
	void unary_returnsProduct_endToEnd() throws Exception {
		mockMvc.perform(get("/demo/grpc/unary/products/p1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Widget"));
	}

	@Test
	void unary_returns404_whenProductUnknown() throws Exception {
		mockMvc.perform(get("/demo/grpc/unary/products/unknown")).andExpect(status().isNotFound());
	}

	@Test
	void unary_returns502_onSimulatedServerError() throws Exception {
		mockMvc.perform(get("/demo/grpc/unary/products/fail-trigger")).andExpect(status().isBadGateway());
	}

	@Test
	void serverStreaming_returnsAllProducts_endToEnd() throws Exception {
		mockMvc.perform(get("/demo/grpc/server-streaming/products")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void clientStreaming_returnsSummary_endToEnd() throws Exception {
		mockMvc.perform(post("/demo/grpc/client-streaming/orders").contentType("application/json")
				.content("[{\"productId\":\"p1\",\"quantity\":2}]")).andExpect(status().isOk())
				.andExpect(jsonPath("$.orderCount").value(1));
	}

	@Test
	void bidiStreaming_echoesEachUpdate_endToEnd() throws Exception {
		mockMvc.perform(post("/demo/grpc/bidi-streaming/order-status").contentType("application/json")
				.content("[{\"orderId\":\"o1\",\"status\":\"PLACED\"}]")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("ACKNOWLEDGED:PLACED"));
	}
}
