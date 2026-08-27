package com.testingai.cassandra.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.cassandra.consistency.ConsistencyDemoService;
import com.testingai.cassandra.consistency.ConsistencyReadResult;
import com.testingai.cassandra.counter.OrderCountService;
import com.testingai.cassandra.crud.Product;
import com.testingai.cassandra.crud.ProductService;
import com.testingai.cassandra.datamodeling.OrderByCustomer;
import com.testingai.cassandra.datamodeling.OrderByProduct;
import com.testingai.cassandra.datamodeling.OrderService;
import com.testingai.cassandra.ttl.ProductView;
import com.testingai.cassandra.ttl.RecentlyViewedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
	private ConsistencyDemoService consistencyDemoService;
	@MockitoBean
	private RecentlyViewedService recentlyViewedService;
	@MockitoBean
	private OrderCountService orderCountService;

	@Test
	void createProduct_shouldReturn200AndDelegate() throws Exception {
		Product product = new Product(null, "Widget", new BigDecimal("9.99"), 100);
		when(productService.create(product)).thenReturn(product);

		mockMvc.perform(post("/demo/products").contentType("application/json")
				.content(objectMapper.writeValueAsString(product))).andExpect(status().isOk());

		verify(productService).create(product);
	}

	@Test
	void getProduct_shouldReturn200RecordViewAndDelegate() throws Exception {
		UUID id = UUID.randomUUID();
		Product product = new Product(id, "Widget", new BigDecimal("9.99"), 100);
		when(productService.findById(id)).thenReturn(product);

		mockMvc.perform(get("/demo/products/" + id)).andExpect(status().isOk());

		verify(productService).findById(id);
		verify(recentlyViewedService).recordView(id);
	}

	@Test
	void updateProduct_shouldReturn200AndDelegate() throws Exception {
		UUID id = UUID.randomUUID();
		Product update = new Product(null, "Widget v2", new BigDecimal("12.99"), 50);
		mockMvc.perform(put("/demo/products/" + id).contentType("application/json")
				.content(objectMapper.writeValueAsString(update))).andExpect(status().isOk());
		verify(productService).update(id, update);
	}

	@Test
	void deleteProduct_shouldReturn200AndDelegate() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(delete("/demo/products/" + id)).andExpect(status().isOk());
		verify(productService).delete(id);
	}

	@Test
	void placeOrder_shouldReturn200AndDelegate() throws Exception {
		UUID productId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		OrderByCustomer order = new OrderByCustomer("cust-1", orderId, productId, 2, new BigDecimal("10.00"),
				new BigDecimal("20.00"));
		when(orderService.placeOrder("cust-1", productId, 2)).thenReturn(order);

		mockMvc.perform(post("/demo/orders").contentType("application/json")
				.content("{\"customerId\":\"cust-1\",\"productId\":\"" + productId + "\",\"quantity\":2}"))
				.andExpect(status().isOk());

		verify(orderService).placeOrder("cust-1", productId, 2);
	}

	@Test
	void ordersByCustomer_shouldReturn200AndDelegate() throws Exception {
		when(orderService.findByCustomer("cust-1")).thenReturn(List.of());

		mockMvc.perform(get("/demo/orders/by-customer/cust-1")).andExpect(status().isOk());

		verify(orderService).findByCustomer("cust-1");
	}

	@Test
	void ordersByProduct_shouldReturn200AndDelegate() throws Exception {
		UUID productId = UUID.randomUUID();
		when(orderService.findByProduct(productId)).thenReturn(List.<OrderByProduct>of());

		mockMvc.perform(get("/demo/orders/by-product/" + productId)).andExpect(status().isOk());

		verify(orderService).findByProduct(productId);
	}

	@Test
	void consistencyRead_shouldReturn200AndDelegate() throws Exception {
		UUID productId = UUID.randomUUID();
		Product product = new Product(productId, "Widget", new BigDecimal("9.99"), 100);
		when(consistencyDemoService.readAt(productId, "QUORUM"))
				.thenReturn(new ConsistencyReadResult(product, "QUORUM", 5L));

		mockMvc.perform(get("/demo/products/" + productId + "/consistency").param("level", "QUORUM"))
				.andExpect(status().isOk());

		verify(consistencyDemoService).readAt(productId, "QUORUM");
	}

	@Test
	void recentlyViewed_shouldReturn200AndDelegate() throws Exception {
		UUID productId = UUID.randomUUID();
		when(recentlyViewedService.listViews(productId)).thenReturn(List.of());

		mockMvc.perform(get("/demo/products/" + productId + "/recently-viewed")).andExpect(status().isOk());

		verify(recentlyViewedService).listViews(productId);
	}

	@Test
	void orderCount_shouldReturn200AndDelegate() throws Exception {
		UUID productId = UUID.randomUUID();
		when(orderCountService.getCount(productId)).thenReturn(7L);

		mockMvc.perform(get("/demo/products/" + productId + "/order-count")).andExpect(status().isOk());

		verify(orderCountService).getCount(productId);
	}
}
