package com.testingai.grpc.server.service;

import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import com.testingai.grpc.server.domain.SampleDataService;
import com.testingai.grpc.server.interceptor.RequestIdServerInterceptor;
import com.testingai.grpc.server.util.FailureSimulator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Slf4j
@GrpcService
public class ProductCatalogServiceImpl extends ProductCatalogServiceGrpc.ProductCatalogServiceImplBase {

	private final SampleDataService sampleDataService;
	private final long streamDelayMillis;

	public ProductCatalogServiceImpl(SampleDataService sampleDataService,
			@Value("${demo.stream-delay-millis:300}") long streamDelayMillis) {
		this.sampleDataService = sampleDataService;
		this.streamDelayMillis = streamDelayMillis;
	}

	@Override
	public void getProduct(ProductRequest request, StreamObserver<ProductResponse> responseObserver) {
		String requestId = currentRequestId();
		log.info("[GetProduct][{}] request received: productId={}", requestId, request.getProductId());
		try {
			FailureSimulator.maybeThrow("getProduct");
		} catch (RuntimeException e) {
			log.warn("[GetProduct][{}] simulated failure for productId={}", requestId, request.getProductId());
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
			return;
		}
		sampleDataService.findProduct(request.getProductId()).ifPresentOrElse(product -> {
			log.info("[GetProduct][{}] found product: {} ({})", requestId, product.getName(), product.getProductId());
			responseObserver.onNext(product);
			responseObserver.onCompleted();
		}, () -> {
			log.info("[GetProduct][{}] product not found: productId={}", requestId, request.getProductId());
			responseObserver.onError(Status.NOT_FOUND.withDescription("Unknown product: " + request.getProductId())
					.asRuntimeException());
		});
	}

	@Override
	public void listProducts(ListProductsRequest request, StreamObserver<ProductResponse> responseObserver) {
		String requestId = currentRequestId();
		List<ProductResponse> products = sampleDataService.listProducts();
		log.info("[ListProducts][{}] streaming {} products", requestId, products.size());
		int index = 0;
		for (ProductResponse product : products) {
			index++;
			try {
				FailureSimulator.maybeThrow("listProducts");
			} catch (RuntimeException e) {
				log.warn("[ListProducts][{}] simulated failure after {} of {} products", requestId, index - 1,
						products.size());
				responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
				return;
			}
			log.info("[ListProducts][{}] sending product {}/{}: {} ({})", requestId, index, products.size(),
					product.getName(), product.getProductId());
			responseObserver.onNext(product);
			sleepBriefly();
		}
		log.info("[ListProducts][{}] completed: {} products sent", requestId, products.size());
		responseObserver.onCompleted();
	}

	@Override
	public StreamObserver<OrderRequest> uploadOrders(StreamObserver<OrderSummary> responseObserver) {
		String requestId = currentRequestId();
		log.info("[UploadOrders][{}] starting upload stream", requestId);
		return new StreamObserver<>() {

			private int orderCount = 0;
			private long totalPriceCents = 0;
			private boolean errored = false;

			@Override
			public void onNext(OrderRequest order) {
				if (errored) {
					return;
				}
				try {
					FailureSimulator.maybeThrow("uploadOrders");
				} catch (RuntimeException e) {
					errored = true;
					log.warn("[UploadOrders][{}] simulated failure after {} orders", requestId, orderCount);
					responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
					return;
				}
				sampleDataService.findProduct(order.getProductId()).ifPresentOrElse(product -> {
					orderCount++;
					long lineTotalCents = product.getPriceCents() * order.getQuantity();
					totalPriceCents += lineTotalCents;
					log.info("[UploadOrders][{}] received order {}: {} x {} = {} cents", requestId, orderCount,
							order.getQuantity(), product.getName(), lineTotalCents);
				}, () -> log.warn("[UploadOrders][{}] skipping unknown productId={}", requestId, order.getProductId()));
				sleepBriefly();
			}

			@Override
			public void onError(Throwable t) {
				log.warn("[UploadOrders][{}] client cancelled the upload: {}", requestId, t.getMessage());
			}

			@Override
			public void onCompleted() {
				if (errored) {
					return;
				}
				log.info("[UploadOrders][{}] completed: {} orders, {} cents total", requestId, orderCount,
						totalPriceCents);
				responseObserver.onNext(OrderSummary.newBuilder().setOrderCount(orderCount)
						.setTotalPriceCents(totalPriceCents).build());
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public StreamObserver<OrderStatusUpdate> streamOrderStatus(StreamObserver<OrderStatusUpdate> responseObserver) {
		String requestId = currentRequestId();
		log.info("[StreamOrderStatus][{}] starting duplex stream", requestId);
		return new StreamObserver<>() {

			private boolean errored = false;

			@Override
			public void onNext(OrderStatusUpdate update) {
				if (errored) {
					return;
				}
				try {
					FailureSimulator.maybeThrow("streamOrderStatus");
				} catch (RuntimeException e) {
					errored = true;
					log.warn("[StreamOrderStatus][{}] simulated failure for orderId={}", requestId,
							update.getOrderId());
					responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
					return;
				}
				log.info("[StreamOrderStatus][{}] received update: orderId={} status={}", requestId,
						update.getOrderId(), update.getStatus());
				OrderStatusUpdate ack = OrderStatusUpdate.newBuilder().setOrderId(update.getOrderId())
						.setStatus("ACKNOWLEDGED:" + update.getStatus()).build();
				log.info("[StreamOrderStatus][{}] acknowledging: orderId={} status={}", requestId, ack.getOrderId(),
						ack.getStatus());
				responseObserver.onNext(ack);
				sleepBriefly();
			}

			@Override
			public void onError(Throwable t) {
				log.warn("[StreamOrderStatus][{}] client cancelled the stream: {}", requestId, t.getMessage());
			}

			@Override
			public void onCompleted() {
				if (!errored) {
					log.info("[StreamOrderStatus][{}] stream completed", requestId);
					responseObserver.onCompleted();
				}
			}
		};
	}

	private static String currentRequestId() {
		String requestId = RequestIdServerInterceptor.REQUEST_ID_CONTEXT_KEY.get();
		return requestId != null ? requestId : "no-id";
	}

	private void sleepBriefly() {
		if (streamDelayMillis <= 0) {
			return;
		}
		try {
			Thread.sleep(streamDelayMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
