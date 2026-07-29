package com.testingai.graphql.domain;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProductCatalogService {

	private static final List<String> PRODUCT_NAMES = List.of("Widget", "Gadget", "Gizmo", "Doohickey", "Thingamajig",
			"Contraption", "Doodad", "Whatsit", "Gizmotron", "Thingamabob");
	private static final List<String> PRODUCT_VARIANTS = List.of("Mini", "Standard", "Pro", "Max");

	private final List<Product> products = buildCatalog();

	private static List<Product> buildCatalog() {
		List<Product> catalog = new ArrayList<>();
		int id = 1;
		for (String variant : PRODUCT_VARIANTS) {
			for (String name : PRODUCT_NAMES) {
				long priceCents = 499 + (id * 137L % 4500);
				catalog.add(new Product("p" + id, variant + " " + name, priceCents));
				id++;
			}
		}
		return List.copyOf(catalog);
	}

	public Optional<Product> findProduct(String productId) {
		return products.stream().filter(product -> product.id().equals(productId)).findFirst();
	}

	public List<Product> listProducts() {
		return products;
	}
}
