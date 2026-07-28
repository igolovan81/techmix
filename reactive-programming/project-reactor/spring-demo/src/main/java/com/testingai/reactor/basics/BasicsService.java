package com.testingai.reactor.basics;

import com.testingai.reactor.domain.Product;
import com.testingai.reactor.domain.ProductWithDiscount;
import com.testingai.reactor.domain.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BasicsService {

	private static final double STANDARD_DISCOUNT_RATE = 0.10;

	private final SampleDataService sampleDataService;

	public Flux<Product> allProducts() {
		return Flux.fromIterable(sampleDataService.catalog());
	}

	public Mono<Product> productById(String id) {
		return allProducts().filter(product -> product.id().equals(id)).next();
	}

	public Flux<Product> generatedProducts(int count) {
		List<Product> catalog = sampleDataService.catalog();
		return Flux.generate(() -> 0, (index, sink) -> {
			if (index >= count) {
				sink.complete();
				return index;
			}
			Product source = catalog.get(index % catalog.size());
			sink.next(new Product(source.id() + "-" + index, source.name(), source.priceCents()));
			return index + 1;
		});
	}

	public Flux<ProductWithDiscount> discountedCatalog() {
		Flux<Double> discountRates = Flux.fromIterable(sampleDataService.catalog())
				.map(product -> STANDARD_DISCOUNT_RATE);
		return Flux.zip(allProducts(), discountRates, this::applyDiscount);
	}

	public Flux<Product> combinedViaConcat(Flux<Product> first, Flux<Product> second) {
		return Flux.concat(first, second);
	}

	public Flux<Product> combinedViaMerge(Flux<Product> first, Flux<Product> second) {
		return Flux.merge(first, second);
	}

	private ProductWithDiscount applyDiscount(Product product, double discountRate) {
		long discountedPriceCents = Math.round(product.priceCents() * (1 - discountRate));
		return new ProductWithDiscount(product, discountedPriceCents);
	}
}
