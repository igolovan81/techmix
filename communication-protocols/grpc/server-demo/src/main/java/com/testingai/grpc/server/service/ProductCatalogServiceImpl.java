package com.testingai.grpc.server.service;

import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import com.testingai.grpc.server.domain.SampleDataService;
import com.testingai.grpc.server.util.FailureSimulator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class ProductCatalogServiceImpl extends ProductCatalogServiceGrpc.ProductCatalogServiceImplBase {

	private final SampleDataService sampleDataService;

	public ProductCatalogServiceImpl(SampleDataService sampleDataService) {
		this.sampleDataService = sampleDataService;
	}

	@Override
	public void getProduct(ProductRequest request, StreamObserver<ProductResponse> responseObserver) {
		try {
			FailureSimulator.maybeThrow("getProduct");
		} catch (RuntimeException e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
			return;
		}
		sampleDataService.findProduct(request.getProductId()).ifPresentOrElse(product -> {
			responseObserver.onNext(product);
			responseObserver.onCompleted();
		}, () -> responseObserver.onError(Status.NOT_FOUND
				.withDescription("Unknown product: " + request.getProductId()).asRuntimeException()));
	}

	@Override
	public void listProducts(ListProductsRequest request, StreamObserver<ProductResponse> responseObserver) {
		for (ProductResponse product : sampleDataService.listProducts()) {
			try {
				FailureSimulator.maybeThrow("listProducts");
			} catch (RuntimeException e) {
				responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
				return;
			}
			responseObserver.onNext(product);
		}
		responseObserver.onCompleted();
	}

	@Override
	public StreamObserver<OrderRequest> uploadOrders(StreamObserver<OrderSummary> responseObserver) {
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
					responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
					return;
				}
				sampleDataService.findProduct(order.getProductId()).ifPresent(product -> {
					orderCount++;
					totalPriceCents += product.getPriceCents() * order.getQuantity();
				});
			}

			@Override
			public void onError(Throwable t) {
				// client cancelled the upload; nothing to clean up
			}

			@Override
			public void onCompleted() {
				if (errored) {
					return;
				}
				responseObserver.onNext(OrderSummary.newBuilder().setOrderCount(orderCount)
						.setTotalPriceCents(totalPriceCents).build());
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public StreamObserver<OrderStatusUpdate> streamOrderStatus(StreamObserver<OrderStatusUpdate> responseObserver) {
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
					responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
					return;
				}
				responseObserver.onNext(OrderStatusUpdate.newBuilder().setOrderId(update.getOrderId())
						.setStatus("ACKNOWLEDGED:" + update.getStatus()).build());
			}

			@Override
			public void onError(Throwable t) {
				// client cancelled the stream; nothing to clean up
			}

			@Override
			public void onCompleted() {
				if (!errored) {
					responseObserver.onCompleted();
				}
			}
		};
	}
}
