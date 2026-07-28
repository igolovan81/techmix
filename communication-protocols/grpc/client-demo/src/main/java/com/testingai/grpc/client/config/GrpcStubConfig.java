package com.testingai.grpc.client.config;

import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcStubConfig {

	@GrpcClient("catalog-service")
	private ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub blockingStub;

	@GrpcClient("catalog-service")
	private ProductCatalogServiceGrpc.ProductCatalogServiceStub asyncStub;

	@Bean
	public ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub productCatalogBlockingStub() {
		return blockingStub;
	}

	@Bean
	public ProductCatalogServiceGrpc.ProductCatalogServiceStub productCatalogAsyncStub() {
		return asyncStub;
	}
}
