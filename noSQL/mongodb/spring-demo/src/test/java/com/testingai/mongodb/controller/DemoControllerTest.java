package com.testingai.mongodb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.mongodb.aggregation.OrderAggregationService;
import com.testingai.mongodb.aggregation.StatusSummary;
import com.testingai.mongodb.crud.Product;
import com.testingai.mongodb.crud.ProductService;
import com.testingai.mongodb.search.ProductSearchService;
import com.testingai.mongodb.transaction.Order;
import com.testingai.mongodb.transaction.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ProductService productService;
	@MockitoBean
	private OrderService orderService;
	@MockitoBean
	private OrderAggregationService aggregationService;
	@MockitoBean
	private ProductSearchService productSearchService;

	@Test
	void createProduct_shouldReturn200AndDelegate() throws Exception {
		Product product = new Product(null, "Widget", 9.99, 100);
		when(productService.create(product)).thenReturn(product);

		mockMvc.perform(post("/demo/products").contentType("application/json")
				.content(objectMapper.writeValueAsString(product))).andExpect(status().isOk());

		verify(productService).create(product);
	}

	@Test
	void getProduct_shouldReturn200AndDelegate() throws Exception {
		mockMvc.perform(get("/demo/products/abc123")).andExpect(status().isOk());
		verify(productService).findById("abc123");
	}

	@Test
	void updateProduct_shouldReturn200AndDelegate() throws Exception {
		Product update = new Product(null, "Widget v2", 12.99, 50);
		mockMvc.perform(put("/demo/products/abc123").contentType("application/json")
				.content(objectMapper.writeValueAsString(update))).andExpect(status().isOk());
		verify(productService).update("abc123", update);
	}

	@Test
	void deleteProduct_shouldReturn200AndDelegate() throws Exception {
		mockMvc.perform(delete("/demo/products/abc123")).andExpect(status().isOk());
		verify(productService).delete("abc123");
	}

	@Test
	void placeOrder_shouldReturn200AndDelegate() throws Exception {
		Order order = new Order("o1", "p1", 2, 10.0, 20.0, "PLACED");
		when(orderService.placeOrder("p1", 2)).thenReturn(order);

		mockMvc.perform(
				post("/demo/orders").contentType("application/json").content("{\"productId\":\"p1\",\"quantity\":2}"))
				.andExpect(status().isOk());

		verify(orderService).placeOrder("p1", 2);
	}

	@Test
	void aggregation_shouldReturn200AndDelegate() throws Exception {
		when(aggregationService.summarizeByStatus()).thenReturn(List.of(new StatusSummary("PLACED", 1, 20.0)));

		mockMvc.perform(get("/demo/aggregation")).andExpect(status().isOk());

		verify(aggregationService).summarizeByStatus();
	}

	@Test
	void searchProducts_shouldReturn200AndDelegate() throws Exception {
		when(productSearchService.searchByText("Widget")).thenReturn(List.of());

		mockMvc.perform(get("/demo/products/search").param("q", "Widget")).andExpect(status().isOk());

		verify(productSearchService).searchByText("Widget");
	}

	@Test
	void productsByPriceRange_shouldReturn200AndDelegate() throws Exception {
		when(productSearchService.findByPriceRange(5.0, 50.0)).thenReturn(List.of());

		mockMvc.perform(get("/demo/products/price-range").param("min", "5.0").param("max", "50.0"))
				.andExpect(status().isOk());

		verify(productSearchService).findByPriceRange(5.0, 50.0);
	}
}
