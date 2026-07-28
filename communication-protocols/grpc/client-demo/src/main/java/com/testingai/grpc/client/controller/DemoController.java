package com.testingai.grpc.client.controller;

import com.testingai.grpc.client.dto.OrderRequestDto;
import com.testingai.grpc.client.dto.OrderStatusUpdateDto;
import com.testingai.grpc.client.dto.OrderSummaryDto;
import com.testingai.grpc.client.dto.ProductDto;
import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/demo/grpc")
@RequiredArgsConstructor
public class DemoController {

	private final ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub blockingStub;
	private final ProductCatalogServiceGrpc.ProductCatalogServiceStub asyncStub;

	@GetMapping("/unary/products/{productId}")
	public ResponseEntity<ProductDto> getProduct(@PathVariable String productId) {
		ProductResponse response = blockingStub.getProduct(ProductRequest.newBuilder().setProductId(productId).build());
		return ResponseEntity.ok(toDto(response));
	}

	@GetMapping("/server-streaming/products")
	public ResponseEntity<List<ProductDto>> listProducts() {
		List<ProductDto> products = new ArrayList<>();
		blockingStub.listProducts(ListProductsRequest.getDefaultInstance())
				.forEachRemaining(product -> products.add(toDto(product)));
		return ResponseEntity.ok(products);
	}

	@PostMapping("/client-streaming/orders")
	public ResponseEntity<OrderSummaryDto> uploadOrders(@RequestBody List<OrderRequestDto> orders) {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<OrderSummary> result = new AtomicReference<>();
		AtomicReference<Throwable> error = new AtomicReference<>();

		StreamObserver<OrderRequest> requestObserver = asyncStub.uploadOrders(new StreamObserver<>() {

			@Override
			public void onNext(OrderSummary value) {
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
		});

		orders.forEach(order -> requestObserver
				.onNext(OrderRequest.newBuilder().setProductId(order.productId()).setQuantity(order.quantity()).build()));
		requestObserver.onCompleted();

		awaitLatch(latch);
		if (error.get() != null) {
			throw (RuntimeException) error.get();
		}
		return ResponseEntity.ok(toDto(result.get()));
	}

	@PostMapping("/bidi-streaming/order-status")
	public ResponseEntity<List<OrderStatusUpdateDto>> streamOrderStatus(@RequestBody List<OrderStatusUpdateDto> updates) {
		CountDownLatch latch = new CountDownLatch(1);
		List<OrderStatusUpdate> responses = Collections.synchronizedList(new ArrayList<>());
		AtomicReference<Throwable> error = new AtomicReference<>();

		StreamObserver<OrderStatusUpdate> requestObserver = asyncStub.streamOrderStatus(new StreamObserver<>() {

			@Override
			public void onNext(OrderStatusUpdate value) {
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
		});

		updates.forEach(update -> requestObserver.onNext(
				OrderStatusUpdate.newBuilder().setOrderId(update.orderId()).setStatus(update.status()).build()));
		requestObserver.onCompleted();

		awaitLatch(latch);
		if (error.get() != null) {
			throw (RuntimeException) error.get();
		}
		return ResponseEntity.ok(responses.stream().map(this::toDto).toList());
	}

	private void awaitLatch(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
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
