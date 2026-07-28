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

/**
 * Implements {@link ProductCatalogServiceGrpc}, demonstrating all four gRPC RPC patterns against the in-memory
 * {@link SampleDataService} catalog:
 * <ul>
 * <li>{@code GetProduct} — unary</li>
 * <li>{@code ListProducts} — server streaming</li>
 * <li>{@code UploadOrders} — client streaming</li>
 * <li>{@code StreamOrderStatus} — bidirectional streaming</li>
 * </ul>
 * Every RPC calls {@link FailureSimulator#maybeThrow(String)} first; a simulated failure is mapped to a
 * {@code Status.INTERNAL} gRPC error. The three streaming RPCs also pause {@link #streamDelayMillis} between items so
 * their progress is visible when watching the logs live.
 * <p>
 * Every log line is tagged with the request id read from {@link RequestIdServerInterceptor#REQUEST_ID_CONTEXT_KEY} (see
 * {@link #currentRequestId()}), which gRPC's {@link io.grpc.Context} propagates correctly even into the streaming
 * callbacks below, which run on a different thread than the initiating call.
 */
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

	/**
	 * Unary RPC — looks up a single product by id.
	 *
	 * @param request
	 *            the id to look up
	 * @param responseObserver
	 *            receives the product on success, or a {@code NOT_FOUND}/{@code INTERNAL} error
	 */
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

	/**
	 * Server-streaming RPC — sends every product in the catalog as a separate message, pausing
	 * {@link #streamDelayMillis} between each one, then completes the stream.
	 *
	 * @param request
	 *            empty; {@code ListProducts} takes no parameters
	 * @param responseObserver
	 *            receives one {@link ProductResponse} per catalog item, or an {@code INTERNAL} error
	 */
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

	/**
	 * Client-streaming RPC — accepts a stream of orders, accumulating count and total price as each one arrives, and
	 * replies with one {@link OrderSummary} once the client closes its stream.
	 *
	 * @param responseObserver
	 *            receives the single {@link OrderSummary} on completion, or an {@code INTERNAL} error
	 * @return an observer the client streams {@link OrderRequest orders} into
	 */
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

	/**
	 * Bidirectional-streaming RPC — for each incoming status update, immediately echoes back an
	 * {@code "ACKNOWLEDGED:" + status} update, independently of any other updates on the same stream.
	 *
	 * @param responseObserver
	 *            receives one acknowledgement per incoming update, or an {@code INTERNAL} error
	 * @return an observer the client streams {@link OrderStatusUpdate updates} into
	 */
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
