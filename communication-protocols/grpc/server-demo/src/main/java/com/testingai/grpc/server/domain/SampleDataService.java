package com.testingai.grpc.server.domain;

import com.testingai.grpc.proto.ProductResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SampleDataService {

	private final List<ProductResponse> products = List.of(
			ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build(),
			ProductResponse.newBuilder().setProductId("p2").setName("Gadget").setPriceCents(1999).build(),
			ProductResponse.newBuilder().setProductId("p3").setName("Gizmo").setPriceCents(2999).build(),
			ProductResponse.newBuilder().setProductId("p4").setName("Doohickey").setPriceCents(499).build());

	public Optional<ProductResponse> findProduct(String productId) {
		return products.stream().filter(product -> product.getProductId().equals(productId)).findFirst();
	}

	public List<ProductResponse> listProducts() {
		return products;
	}
}
