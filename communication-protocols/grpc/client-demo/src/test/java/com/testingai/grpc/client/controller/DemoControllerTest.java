package com.testingai.grpc.client.controller;

import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DemoControllerTest {

	@Mock
	private ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub blockingStub;
	@Mock
	private ProductCatalogServiceGrpc.ProductCatalogServiceStub asyncStub;
	@Mock
	private StreamObserver<OrderRequest> orderRequestObserver;
	@Mock
	private StreamObserver<OrderStatusUpdate> orderStatusRequestObserver;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		DemoController controller = new DemoController(blockingStub, asyncStub);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new DemoExceptionHandler()).build();
	}

	@Test
	void getProduct_returnsProduct() throws Exception {
		when(blockingStub.getProduct(any())).thenReturn(
				ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build());

		mockMvc.perform(get("/demo/grpc/unary/products/p1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Widget")).andExpect(jsonPath("$.priceCents").value(999));
	}

	@Test
	void getProduct_returns404_whenGrpcReportsNotFound() throws Exception {
		when(blockingStub.getProduct(any()))
				.thenThrow(Status.NOT_FOUND.withDescription("Unknown product: p9").asRuntimeException());

		mockMvc.perform(get("/demo/grpc/unary/products/p9")).andExpect(status().isNotFound());
	}

	@Test
	void listProducts_returnsAllStreamedProducts() throws Exception {
		List<ProductResponse> products = List.of(
				ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build(),
				ProductResponse.newBuilder().setProductId("p2").setName("Gadget").setPriceCents(1999).build());
		when(blockingStub.listProducts(any())).thenReturn(products.iterator());

		mockMvc.perform(get("/demo/grpc/server-streaming/products")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].name").value("Gadget"));
	}

	@Test
	void uploadOrders_returnsSummary_fromAsyncStub() throws Exception {
		when(asyncStub.uploadOrders(any())).thenAnswer(invocation -> {
			StreamObserver<OrderSummary> responseObserver = invocation.getArgument(0);
			responseObserver.onNext(OrderSummary.newBuilder().setOrderCount(2).setTotalPriceCents(2997).build());
			responseObserver.onCompleted();
			return orderRequestObserver;
		});

		mockMvc.perform(post("/demo/grpc/client-streaming/orders").contentType(MediaType.APPLICATION_JSON)
				.content("[{\"productId\":\"p1\",\"quantity\":2}]")).andExpect(status().isOk())
				.andExpect(jsonPath("$.orderCount").value(2)).andExpect(jsonPath("$.totalPriceCents").value(2997));
	}

	@Test
	void uploadOrders_returns502_whenAsyncStubReportsInternalError() throws Exception {
		when(asyncStub.uploadOrders(any())).thenAnswer(invocation -> {
			StreamObserver<OrderSummary> responseObserver = invocation.getArgument(0);
			responseObserver.onError(Status.INTERNAL.withDescription("Simulated 5% failure").asRuntimeException());
			return orderRequestObserver;
		});

		mockMvc.perform(post("/demo/grpc/client-streaming/orders").contentType(MediaType.APPLICATION_JSON)
				.content("[{\"productId\":\"p1\",\"quantity\":2}]")).andExpect(status().isBadGateway());
	}

	@Test
	void streamOrderStatus_returnsEchoedUpdates() throws Exception {
		when(asyncStub.streamOrderStatus(any())).thenAnswer(invocation -> {
			StreamObserver<OrderStatusUpdate> responseObserver = invocation.getArgument(0);
			responseObserver
					.onNext(OrderStatusUpdate.newBuilder().setOrderId("o1").setStatus("ACKNOWLEDGED:PLACED").build());
			responseObserver.onCompleted();
			return orderStatusRequestObserver;
		});

		mockMvc.perform(post("/demo/grpc/bidi-streaming/order-status").contentType(MediaType.APPLICATION_JSON)
				.content("[{\"orderId\":\"o1\",\"status\":\"PLACED\"}]")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("ACKNOWLEDGED:PLACED"));
	}
}
