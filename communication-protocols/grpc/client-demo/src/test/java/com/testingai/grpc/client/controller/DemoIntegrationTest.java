package com.testingai.grpc.client.controller;

import com.testingai.grpc.client.support.FakeProductCatalogService;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grpc.client.catalog-service.address=in-process:demo-integration-test")
@AutoConfigureMockMvc
class DemoIntegrationTest {

	private static final Metadata.Key<String> REQUEST_ID_METADATA_KEY = Metadata.Key.of("x-request-id",
			Metadata.ASCII_STRING_MARSHALLER);

	private static final AtomicReference<String> capturedRequestId = new AtomicReference<>();

	private static Server inProcessServer;

	@Autowired
	private MockMvc mockMvc;

	@BeforeAll
	static void startFakeServer() throws IOException {
		ServerInterceptor requestIdCapturingInterceptor = new ServerInterceptor() {

			@Override
			public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
					ServerCallHandler<ReqT, RespT> next) {
				capturedRequestId.set(headers.get(REQUEST_ID_METADATA_KEY));
				return next.startCall(call, headers);
			}
		};
		inProcessServer = InProcessServerBuilder.forName("demo-integration-test").directExecutor()
				.addService(
						ServerInterceptors.intercept(new FakeProductCatalogService(), requestIdCapturingInterceptor))
				.build().start();
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
	void unary_propagatesRequestIdHeader_toServer() throws Exception {
		mockMvc.perform(get("/demo/grpc/unary/products/p1")).andExpect(status().isOk());

		assertThat(capturedRequestId.get()).isNotNull().hasSize(8);
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
