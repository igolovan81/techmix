package com.testingai.grpc.server.service;

import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import com.testingai.grpc.server.domain.SampleDataService;
import com.testingai.grpc.server.util.FailureSimulator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceImplTest {

	@Mock
	private StreamObserver<ProductResponse> productObserver;
	@Mock
	private StreamObserver<OrderSummary> orderSummaryObserver;
	@Mock
	private StreamObserver<OrderStatusUpdate> orderStatusObserver;

	private final SampleDataService sampleDataService = new SampleDataService();

	private ProductCatalogServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new ProductCatalogServiceImpl(sampleDataService, 0L);
	}

	@Test
	void getProduct_returnsProduct_whenFound() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			service.getProduct(ProductRequest.newBuilder().setProductId("p1").build(), productObserver);

			ArgumentCaptor<ProductResponse> captor = ArgumentCaptor.forClass(ProductResponse.class);
			verify(productObserver).onNext(captor.capture());
			verify(productObserver).onCompleted();
			assertThat(captor.getValue().getName()).isEqualTo("Mini Widget");
		}
	}

	@Test
	void getProduct_sendsNotFound_whenProductUnknown() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			service.getProduct(ProductRequest.newBuilder().setProductId("unknown").build(), productObserver);

			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(productObserver).onError(captor.capture());
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.NOT_FOUND);
		}
	}

	@Test
	void getProduct_sendsInternalError_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			service.getProduct(ProductRequest.newBuilder().setProductId("p1").build(), productObserver);

			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(productObserver).onError(captor.capture());
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
		}
	}

	@Test
	void listProducts_streamsAllProducts_whenNoFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			service.listProducts(ListProductsRequest.getDefaultInstance(), productObserver);

			verify(productObserver, times(40)).onNext(any());
			verify(productObserver).onCompleted();
		}
	}

	@Test
	void listProducts_sendsInternalError_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			service.listProducts(ListProductsRequest.getDefaultInstance(), productObserver);

			verify(productObserver, never()).onCompleted();
			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(productObserver).onError(captor.capture());
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
		}
	}

	@Test
	void uploadOrders_returnsSummary_whenNoFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			StreamObserver<OrderRequest> requestObserver = service.uploadOrders(orderSummaryObserver);
			requestObserver.onNext(OrderRequest.newBuilder().setProductId("p1").setQuantity(2).build());
			requestObserver.onNext(OrderRequest.newBuilder().setProductId("p2").setQuantity(1).build());
			requestObserver.onCompleted();

			ArgumentCaptor<OrderSummary> captor = ArgumentCaptor.forClass(OrderSummary.class);
			verify(orderSummaryObserver).onNext(captor.capture());
			verify(orderSummaryObserver).onCompleted();
			long p1PriceCents = sampleDataService.findProduct("p1").orElseThrow().getPriceCents();
			long p2PriceCents = sampleDataService.findProduct("p2").orElseThrow().getPriceCents();
			assertThat(captor.getValue().getOrderCount()).isEqualTo(2);
			assertThat(captor.getValue().getTotalPriceCents()).isEqualTo(p1PriceCents * 2 + p2PriceCents);
		}
	}

	@Test
	void uploadOrders_sendsInternalError_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			StreamObserver<OrderRequest> requestObserver = service.uploadOrders(orderSummaryObserver);
			requestObserver.onNext(OrderRequest.newBuilder().setProductId("p1").setQuantity(1).build());
			requestObserver.onCompleted();

			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(orderSummaryObserver).onError(captor.capture());
			verify(orderSummaryObserver, never()).onCompleted();
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
		}
	}

	@Test
	void streamOrderStatus_echoesEachUpdate_whenNoFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			StreamObserver<OrderStatusUpdate> requestObserver = service.streamOrderStatus(orderStatusObserver);
			requestObserver.onNext(OrderStatusUpdate.newBuilder().setOrderId("o1").setStatus("PLACED").build());
			requestObserver.onCompleted();

			ArgumentCaptor<OrderStatusUpdate> captor = ArgumentCaptor.forClass(OrderStatusUpdate.class);
			verify(orderStatusObserver).onNext(captor.capture());
			verify(orderStatusObserver).onCompleted();
			assertThat(captor.getValue().getStatus()).isEqualTo("ACKNOWLEDGED:PLACED");
		}
	}

	@Test
	void streamOrderStatus_sendsInternalError_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			StreamObserver<OrderStatusUpdate> requestObserver = service.streamOrderStatus(orderStatusObserver);
			requestObserver.onNext(OrderStatusUpdate.newBuilder().setOrderId("o1").setStatus("PLACED").build());

			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(orderStatusObserver).onError(captor.capture());
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
		}
	}
}
