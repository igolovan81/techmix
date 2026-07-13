package com.testingai.freemarker.controller;

import com.testingai.freemarker.config.FreemarkerConfig;
import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.OrderItem;
import com.testingai.freemarker.model.Product;
import com.testingai.freemarker.service.SampleDataService;
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

@WebMvcTest(DemoController.class)
@Import(FreemarkerConfig.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SampleDataService sampleDataService;

	@Test
	void dataModel_shouldRenderBothRecordAndMapAccess() throws Exception {
		mockMvc.perform(get("/demo/data-model")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Record (method-call access)")))
				.andExpect(content().string(containsString("Map (property access)")))
				.andExpect(content().string(containsString("9.99")));
	}

	@Test
	void ifList_shouldMarkOutOfStockProducts() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100),
						new Product("p3", "Gizmo", new BigDecimal("29.99"), 0)));

		mockMvc.perform(get("/demo/directives/if-list")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget - in stock")))
				.andExpect(content().string(containsString("Gizmo - out of stock")));
	}

	@Test
	void switchDirective_shouldMapConfirmedStatusToShippingMessage() throws Exception {
		Order confirmed = new Order("o1", "Alice", List.of(), new BigDecimal("0"), "CONFIRMED",
				Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findAllOrders()).thenReturn(List.of(confirmed));

		mockMvc.perform(get("/demo/directives/switch")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Confirmed and ready to ship")));
	}

	@Test
	void switchDirective_shouldFallBackToDefaultCaseWhenStatusNull() throws Exception {
		Order pending = new Order("o2", "Bob", List.of(), new BigDecimal("0"), null,
				Instant.parse("2026-07-05T08:00:00Z"));
		when(sampleDataService.findAllOrders()).thenReturn(List.of(pending));

		mockMvc.perform(get("/demo/directives/switch")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Awaiting confirmation")));
	}

	@Test
	void macros_shouldRenderOneRowPerProduct() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/demo/macros")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget")))
				.andExpect(content().string(containsString("9.99")));
	}

	@Test
	void functions_shouldApplyDiscountViaUserDefinedFunction() throws Exception {
		mockMvc.perform(get("/demo/functions")).andExpect(status().isOk())
				.andExpect(content().string(containsString("8.99")));
	}

	@Test
	void builtins_shouldFormatStringNumberAndDate() throws Exception {
		mockMvc.perform(get("/demo/builtins")).andExpect(status().isOk())
				.andExpect(content().string(containsString("WIDGET")))
				.andExpect(content().string(containsString("9.90")))
				.andExpect(content().string(containsString("2026-07-01")));
	}

	@Test
	void composition_shouldReuseTheSharedLayoutMacro() throws Exception {
		mockMvc.perform(get("/demo/composition")).andExpect(status().isOk())
				.andExpect(content().string(containsString("FreeMarker Demo")))
				.andExpect(content().string(containsString("Composed Fragment")));
	}

	@Test
	void nullSafety_shouldApplyDefaultOperatorWhenStatusMissing() throws Exception {
		Order sparseOrder = new Order("o2", "Bob",
				List.of(new OrderItem("p4", "Doohickey", 3, new BigDecimal("14.97"))), new BigDecimal("14.97"), null,
				Instant.parse("2026-07-05T08:00:00Z"));
		when(sampleDataService.findOrder("o2")).thenReturn(Optional.of(sparseOrder));

		mockMvc.perform(get("/demo/null-safety")).andExpect(status().isOk())
				.andExpect(content().string(containsString("pending")))
				.andExpect(content().string(containsString("no")));
	}
}
