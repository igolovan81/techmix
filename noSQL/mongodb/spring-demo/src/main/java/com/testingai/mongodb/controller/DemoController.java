package com.testingai.mongodb.controller;

import com.testingai.mongodb.aggregation.OrderAggregationService;
import com.testingai.mongodb.aggregation.StatusSummary;
import com.testingai.mongodb.crud.Product;
import com.testingai.mongodb.crud.ProductService;
import com.testingai.mongodb.search.ProductSearchService;
import com.testingai.mongodb.transaction.Order;
import com.testingai.mongodb.transaction.OrderService;
import com.testingai.mongodb.transaction.PlaceOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

	private final ProductService productService;
	private final OrderService orderService;
	private final OrderAggregationService aggregationService;
	private final ProductSearchService productSearchService;

	@PostMapping("/products")
	public Product createProduct(@RequestBody Product product) {
		return productService.create(product);
	}

	@GetMapping("/products/{id}")
	public Product getProduct(@PathVariable String id) {
		return productService.findById(id);
	}

	@PutMapping("/products/{id}")
	public Product updateProduct(@PathVariable String id, @RequestBody Product product) {
		return productService.update(id, product);
	}

	@DeleteMapping("/products/{id}")
	public void deleteProduct(@PathVariable String id) {
		productService.delete(id);
	}

	@GetMapping("/products/search")
	public List<Product> searchProducts(@RequestParam String q) {
		return productSearchService.searchByText(q);
	}

	@GetMapping("/products/price-range")
	public List<Product> productsByPriceRange(@RequestParam double min, @RequestParam double max) {
		return productSearchService.findByPriceRange(min, max);
	}

	@PostMapping("/orders")
	public Order placeOrder(@RequestBody PlaceOrderRequest request) {
		return orderService.placeOrder(request.productId(), request.quantity());
	}

	@GetMapping("/aggregation")
	public List<StatusSummary> aggregation() {
		return aggregationService.summarizeByStatus();
	}
}
