package com.testingai.grpc.server.domain;

import com.testingai.grpc.proto.ProductResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SampleDataService {

	private static final List<String> PRODUCT_NAMES = List.of("Widget", "Gadget", "Gizmo", "Doohickey", "Thingamajig",
			"Contraption", "Doodad", "Whatsit", "Gizmotron", "Thingamabob");
	private static final List<String> PRODUCT_VARIANTS = List.of("Mini", "Standard", "Pro", "Max");

	private final List<ProductResponse> products = buildCatalog();

	private static List<ProductResponse> buildCatalog() {
		List<ProductResponse> catalog = new ArrayList<>();
		int id = 1;
		for (String variant : PRODUCT_VARIANTS) {
			for (String name : PRODUCT_NAMES) {
				long priceCents = 499 + (id * 137L % 4500);
				catalog.add(ProductResponse.newBuilder().setProductId("p" + id).setName(variant + " " + name)
						.setPriceCents(priceCents).build());
				id++;
			}
		}
		return List.copyOf(catalog);
	}

	public Optional<ProductResponse> findProduct(String productId) {
		return products.stream().filter(product -> product.getProductId().equals(productId)).findFirst();
	}

	public List<ProductResponse> listProducts() {
		return products;
	}
}
