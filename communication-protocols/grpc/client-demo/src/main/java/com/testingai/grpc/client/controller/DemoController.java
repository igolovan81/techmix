package com.testingai.grpc.client.controller;

import com.testingai.grpc.client.dto.OrderRequestDto;
import com.testingai.grpc.client.dto.OrderStatusUpdateDto;
import com.testingai.grpc.client.dto.OrderSummaryDto;
import com.testingai.grpc.client.dto.ProductDto;
import com.testingai.grpc.client.interceptor.RequestIdClientInterceptor;
import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequestMapping("/demo/grpc")
@RequiredArgsConstructor
public class DemoController {

	private static final long RESPONSE_TIMEOUT_SECONDS = 30;

	private final ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub blockingStub;
	private final ProductCatalogServiceGrpc.ProductCatalogServiceStub asyncStub;

	@GetMapping("/unary/products/{productId}")
	public ResponseEntity<ProductDto> getProduct(@PathVariable String productId) {
		String requestId = newRequestId();
		log.info("[GetProduct][{}] requesting productId={}", requestId, productId);
		ProductResponse response = withRequestId(requestId,
				() -> blockingStub.getProduct(ProductRequest.newBuilder().setProductId(productId).build()));
		log.info("[GetProduct][{}] received product: {} ({})", requestId, response.getName(), response.getProductId());
		return ResponseEntity.ok(toDto(response));
	}

	@GetMapping("/server-streaming/products")
	public ResponseEntity<List<ProductDto>> listProducts() {
		String requestId = newRequestId();
		log.info("[ListProducts][{}] requesting product catalog", requestId);
		List<ProductDto> products = new ArrayList<>();
		withRequestId(requestId, () -> blockingStub.listProducts(ListProductsRequest.getDefaultInstance()))
				.forEachRemaining(product -> {
					products.add(toDto(product));
					log.info("[ListProducts][{}] received product #{}: {} ({})", requestId, products.size(),
							product.getName(), product.getProductId());
				});
		log.info("[ListProducts][{}] received {} products total", requestId, products.size());
		return ResponseEntity.ok(products);
	}

	@PostMapping("/client-streaming/orders")
	public ResponseEntity<OrderSummaryDto> uploadOrders(@RequestBody List<OrderRequestDto> orders) {
		String requestId = newRequestId();
		log.info("[UploadOrders][{}] uploading {} orders", requestId, orders.size());
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<OrderSummary> result = new AtomicReference<>();
		AtomicReference<Throwable> error = new AtomicReference<>();

		StreamObserver<OrderRequest> requestObserver = withRequestId(requestId,
				() -> asyncStub.uploadOrders(new StreamObserver<>() {

					@Override
					public void onNext(OrderSummary value) {
						log.info("[UploadOrders][{}] received summary: {} orders, {} cents total", requestId,
								value.getOrderCount(), value.getTotalPriceCents());
						result.set(value);
					}

					@Override
					public void onError(Throwable t) {
						error.set(t);
						latch.countDown();
					}

					@Override
					public void onCompleted() {
						latch.countDown();
					}
				}));

		orders.forEach(order -> {
			log.info("[UploadOrders][{}] sending order: productId={} quantity={}", requestId, order.productId(),
					order.quantity());
			requestObserver.onNext(
					OrderRequest.newBuilder().setProductId(order.productId()).setQuantity(order.quantity()).build());
		});
		requestObserver.onCompleted();

		awaitLatch(latch);
		if (error.get() != null) {
			throw (RuntimeException) error.get();
		}
		return ResponseEntity.ok(toDto(result.get()));
	}

	@PostMapping("/bidi-streaming/order-status")
	public ResponseEntity<List<OrderStatusUpdateDto>> streamOrderStatus(
			@RequestBody List<OrderStatusUpdateDto> updates) {
		String requestId = newRequestId();
		log.info("[StreamOrderStatus][{}] sending {} status updates", requestId, updates.size());
		CountDownLatch latch = new CountDownLatch(1);
		List<OrderStatusUpdate> responses = Collections.synchronizedList(new ArrayList<>());
		AtomicReference<Throwable> error = new AtomicReference<>();

		StreamObserver<OrderStatusUpdate> requestObserver = withRequestId(requestId,
				() -> asyncStub.streamOrderStatus(new StreamObserver<>() {

					@Override
					public void onNext(OrderStatusUpdate value) {
						log.info("[StreamOrderStatus][{}] received ack: orderId={} status={}", requestId,
								value.getOrderId(), value.getStatus());
						responses.add(value);
					}

					@Override
					public void onError(Throwable t) {
						error.set(t);
						latch.countDown();
					}

					@Override
					public void onCompleted() {
						latch.countDown();
					}
				}));

		updates.forEach(update -> {
			log.info("[StreamOrderStatus][{}] sending update: orderId={} status={}", requestId, update.orderId(),
					update.status());
			requestObserver.onNext(
					OrderStatusUpdate.newBuilder().setOrderId(update.orderId()).setStatus(update.status()).build());
		});
		requestObserver.onCompleted();

		awaitLatch(latch);
		if (error.get() != null) {
			throw (RuntimeException) error.get();
		}
		log.info("[StreamOrderStatus][{}] completed: {} acks received", requestId, responses.size());
		return ResponseEntity.ok(responses.stream().map(this::toDto).toList());
	}

	private String newRequestId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/**
	 * Runs {@code call} with {@code requestId} bound to {@link RequestIdClientInterceptor#REQUEST_ID_CONTEXT_KEY} in
	 * the current gRPC {@link Context}, so {@link RequestIdClientInterceptor} can attach it as an {@code x-request-id}
	 * metadata header on the outgoing call. Only the stub-invoking call itself needs to run inside this scope —
	 * outgoing metadata is built once, when the call starts.
	 */
	private <T> T withRequestId(String requestId, Supplier<T> call) {
		Context context = Context.current().withValue(RequestIdClientInterceptor.REQUEST_ID_CONTEXT_KEY, requestId);
		Context previous = context.attach();
		try {
			return call.get();
		} finally {
			context.detach(previous);
		}
	}

	private void awaitLatch(CountDownLatch latch) {
		try {
			latch.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for gRPC response", e);
		}
	}

	private ProductDto toDto(ProductResponse response) {
		return new ProductDto(response.getProductId(), response.getName(), response.getPriceCents());
	}

	private OrderSummaryDto toDto(OrderSummary summary) {
		return new OrderSummaryDto(summary.getOrderCount(), summary.getTotalPriceCents());
	}

	private OrderStatusUpdateDto toDto(OrderStatusUpdate update) {
		return new OrderStatusUpdateDto(update.getOrderId(), update.getStatus());
	}
}
