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

@WebMvcTest(DemoController.class)
@Import(HandlebarsConfig.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SampleDataService sampleDataService;

	@Test
	void variables_shouldEscapeDoubleStacheAndNotEscapeTripleStache() throws Exception {
		mockMvc.perform(get("/demo/variables")).andExpect(status().isOk())
				.andExpect(content().string(containsString("&lt;b&gt;Widget&lt;/b&gt;")))
				.andExpect(content().string(containsString("<b>Widget</b>")));
	}

	@Test
	void builtinHelpers_shouldMarkOutOfStockAndRenderCurrentOrder() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100),
						new Product("p3", "Gizmo", new BigDecimal("29.99"), 0)));
		Order order = new Order("o1", "Alice", List.of(), new BigDecimal("0"), "CONFIRMED",
				Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findOrder("o1")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/demo/helpers/builtin")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget - in stock")))
				.andExpect(content().string(containsString("out of stock")))
				.andExpect(content().string(containsString("Order for Alice")));
	}

	@Test
	void customHelper_shouldFormatEachProductPriceAsCurrency() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/demo/helpers/custom")).andExpect(status().isOk())
				.andExpect(content().string(containsString("$9.99")));
	}

	@Test
	void partials_shouldRenderOrderItemFragmentStandalone() throws Exception {
		Order order = new Order("o1", "Alice", List.of(new OrderItem("p1", "Widget", 2, new BigDecimal("19.98"))),
				new BigDecimal("19.98"), "CONFIRMED", Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findOrder("o1")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/demo/partials")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget")))
				.andExpect(content().string(containsString("$19.98")));
	}

	@Test
	void layout_shouldInjectCustomBodyIntoSharedLayout() throws Exception {
		mockMvc.perform(get("/demo/layout")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Handlebars Demo")))
				.andExpect(content().string(containsString("Custom body content")));
	}

	@Test
	void subexpressions_shouldEvaluateNestedHelperCall() throws Exception {
		mockMvc.perform(get("/demo/subexpressions")).andExpect(status().isOk())
				.andExpect(content().string(containsString("$29.97")));
	}

	@Test
	void precompiled_shouldReportElapsedTimeForBothApproaches() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/demo/precompiled")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Compiled 200 times")))
				.andExpect(content().string(containsString("Precompiled, applied 200 times")));
	}
}
