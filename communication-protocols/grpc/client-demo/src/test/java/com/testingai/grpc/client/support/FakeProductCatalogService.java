package com.testingai.grpc.client.support;

import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class FakeProductCatalogService extends ProductCatalogServiceGrpc.ProductCatalogServiceImplBase {

	@Override
	public void getProduct(ProductRequest request, StreamObserver<ProductResponse> responseObserver) {
		switch (request.getProductId()) {
			case "p1" -> {
				responseObserver
						.onNext(ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build());
				responseObserver.onCompleted();
			}
			case "fail-trigger" ->
				responseObserver.onError(Status.INTERNAL.withDescription("Simulated failure").asRuntimeException());
			default -> responseObserver.onError(
					Status.NOT_FOUND.withDescription("Unknown product: " + request.getProductId()).asRuntimeException());
		}
	}

	@Override
	public void listProducts(ListProductsRequest request, StreamObserver<ProductResponse> responseObserver) {
		responseObserver
				.onNext(ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build());
		responseObserver
				.onNext(ProductResponse.newBuilder().setProductId("p2").setName("Gadget").setPriceCents(1999).build());
		responseObserver.onCompleted();
	}

	@Override
	public StreamObserver<OrderRequest> uploadOrders(StreamObserver<OrderSummary> responseObserver) {
		return new StreamObserver<>() {

			private int count = 0;

			@Override
			public void onNext(OrderRequest value) {
				count++;
			}

			@Override
			public void onError(Throwable t) {
				// client cancelled the upload; nothing to clean up
			}

			@Override
			public void onCompleted() {
				responseObserver
						.onNext(OrderSummary.newBuilder().setOrderCount(count).setTotalPriceCents(count * 999L).build());
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public StreamObserver<OrderStatusUpdate> streamOrderStatus(StreamObserver<OrderStatusUpdate> responseObserver) {
		return new StreamObserver<>() {

			@Override
			public void onNext(OrderStatusUpdate value) {
				responseObserver.onNext(OrderStatusUpdate.newBuilder().setOrderId(value.getOrderId())
						.setStatus("ACKNOWLEDGED:" + value.getStatus()).build());
			}

			@Override
			public void onError(Throwable t) {
				// client cancelled the stream; nothing to clean up
			}

			@Override
			public void onCompleted() {
				responseObserver.onCompleted();
			}
		};
	}
}
