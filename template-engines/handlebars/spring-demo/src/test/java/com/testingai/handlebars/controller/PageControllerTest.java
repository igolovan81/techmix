package com.testingai.handlebars.controller;

import com.testingai.handlebars.config.HandlebarsConfig;
import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.OrderItem;
import com.testingai.handlebars.model.Product;
import com.testingai.handlebars.service.SampleDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageController.class)
@Import(HandlebarsConfig.class)
class PageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SampleDataService sampleDataService;

	@Test
	void products_shouldRenderProductTableWithinLayout() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/pages/products")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget")))
				.andExpect(content().string(containsString("$9.99")))
				.andExpect(content().string(containsString("Handlebars Demo")));
	}

	@Test
	void orderDetail_shouldRenderOrderItemsWithinLayout() throws Exception {
		Order order = new Order("o1", "Alice", List.of(new OrderItem("p1", "Widget", 2, new BigDecimal("19.98"))),
				new BigDecimal("19.98"), "CONFIRMED", Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findOrder("o1")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/pages/orders/o1")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Alice")))
				.andExpect(content().string(containsString("Widget")));
	}

	@Test
	void orderDetail_shouldShowPendingStatusWhenNull() throws Exception {
		Order order = new Order("o2", "Bob", List.of(new OrderItem("p4", "Doohickey", 3, new BigDecimal("14.97"))),
				new BigDecimal("14.97"), null, Instant.parse("2026-07-05T08:00:00Z"));
		when(sampleDataService.findOrder("o2")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/pages/orders/o2")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Status: pending")));
	}

	@Test
	void orderDetail_shouldReturn404WhenOrderMissing() throws Exception {
		when(sampleDataService.findOrder("missing")).thenReturn(Optional.empty());

		mockMvc.perform(get("/pages/orders/missing")).andExpect(status().isNotFound());
	}
}
