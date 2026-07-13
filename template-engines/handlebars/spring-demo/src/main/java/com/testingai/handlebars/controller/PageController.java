package com.testingai.handlebars.controller;

import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.Product;
import com.testingai.handlebars.service.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PageController {

	private final SampleDataService sampleDataService;

	@GetMapping("/pages/products")
	public String products(Model model) {
		List<Product> products = sampleDataService.findAllProducts();
		model.addAttribute("products", products);
		return "products";
	}

	@GetMapping("/pages/orders/{id}")
	public String orderDetail(@PathVariable String id, Model model) {
		Order order = sampleDataService.findOrder(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
		model.addAttribute("order", order);
		return "order-detail";
	}
}
