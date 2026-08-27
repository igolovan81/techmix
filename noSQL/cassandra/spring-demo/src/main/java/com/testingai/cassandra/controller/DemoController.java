package com.testingai.cassandra.controller;

import java.util.List;
import java.util.UUID;

import com.testingai.cassandra.consistency.ConsistencyDemoService;
import com.testingai.cassandra.consistency.ConsistencyReadResult;
import com.testingai.cassandra.counter.OrderCountService;
import com.testingai.cassandra.crud.Product;
import com.testingai.cassandra.crud.ProductService;
import com.testingai.cassandra.datamodeling.OrderByCustomer;
import com.testingai.cassandra.datamodeling.OrderByProduct;
import com.testingai.cassandra.datamodeling.OrderService;
import com.testingai.cassandra.datamodeling.PlaceOrderRequest;
import com.testingai.cassandra.ttl.ProductView;
import com.testingai.cassandra.ttl.RecentlyViewedService;
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

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

	private final ProductService productService;
	private final OrderService orderService;
	private final ConsistencyDemoService consistencyDemoService;
	private final RecentlyViewedService recentlyViewedService;
	private final OrderCountService orderCountService;

	@PostMapping("/products")
	public Product createProduct(@RequestBody Product product) {
		return productService.create(product);
	}

	@GetMapping("/products/{id}")
	public Product getProduct(@PathVariable UUID id) {
		Product product = productService.findById(id);
		recentlyViewedService.recordView(id);
		return product;
	}

	@PutMapping("/products/{id}")
	public Product updateProduct(@PathVariable UUID id, @RequestBody Product product) {
		return productService.update(id, product);
	}

	@DeleteMapping("/products/{id}")
	public void deleteProduct(@PathVariable UUID id) {
		productService.delete(id);
	}

	@PostMapping("/orders")
	public OrderByCustomer placeOrder(@RequestBody PlaceOrderRequest request) {
		return orderService.placeOrder(request.customerId(), request.productId(), request.quantity());
	}

	@GetMapping("/orders/by-customer/{customerId}")
	public List<OrderByCustomer> ordersByCustomer(@PathVariable String customerId) {
		return orderService.findByCustomer(customerId);
	}

	@GetMapping("/orders/by-product/{productId}")
	public List<OrderByProduct> ordersByProduct(@PathVariable UUID productId) {
		return orderService.findByProduct(productId);
	}

	@GetMapping("/products/{id}/consistency")
	public ConsistencyReadResult consistencyRead(@PathVariable UUID id, @RequestParam String level) {
		return consistencyDemoService.readAt(id, level);
	}

	@GetMapping("/products/{id}/recently-viewed")
	public List<ProductView> recentlyViewed(@PathVariable UUID id) {
		return recentlyViewedService.listViews(id);
	}

	@GetMapping("/products/{id}/order-count")
	public long orderCount(@PathVariable UUID id) {
		return orderCountService.getCount(id);
	}
}
