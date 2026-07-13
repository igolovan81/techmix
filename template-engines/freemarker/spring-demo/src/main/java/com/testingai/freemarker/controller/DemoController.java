package com.testingai.freemarker.controller;

import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.Product;
import com.testingai.freemarker.service.SampleDataService;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DemoController {

	private final freemarker.template.Configuration demoFreemarkerConfiguration;
	private final SampleDataService sampleDataService;

	@GetMapping("/demo/data-model")
	public ResponseEntity<String> dataModel() throws IOException, TemplateException {
		Product product = new Product("p1", "Widget", new BigDecimal("9.99"), 100);
		String recordSource = "<p>${product.name()}: $${product.price()?string(\"0.00\")}</p>";
		String fromRecord = render("data-model-record", recordSource, Map.of("product", product));

		Map<String, Object> productAsMap = Map.of("name", "Widget", "price", new BigDecimal("9.99"));
		String mapSource = "<p>${product.name}: $${product.price?string(\"0.00\")}</p>";
		String fromMap = render("data-model-map", mapSource, Map.of("product", productAsMap));

		return html("<h3>Record (method-call access)</h3>" + fromRecord + "<h3>Map (property access)</h3>" + fromMap);
	}

	@GetMapping("/demo/directives/if-list")
	public ResponseEntity<String> ifList() throws IOException, TemplateException {
		String source = """
				<ul>
				<#list products as product>
				  <li><#if product.stock() gt 0>${product.name()} - in stock (${product.stock()})<#else>${product.name()} - out of stock</#if></li>
				</#list>
				</ul>
				""";
		String rendered = render("if-list", source, Map.of("products", sampleDataService.findAllProducts()));
		return html(rendered);
	}

	@GetMapping("/demo/directives/switch")
	public ResponseEntity<String> switchDirective() throws IOException, TemplateException {
		String source = """
				<#list orders as order>
				  <p>Order ${order.id()}:
				  <#switch order.status()!"UNKNOWN">
				    <#case "CONFIRMED">Confirmed and ready to ship<#break>
				    <#case "CANCELLED">Cancelled<#break>
				    <#default>Awaiting confirmation
				  </#switch>
				  </p>
				</#list>
				""";
		String rendered = render("switch", source, Map.of("orders", sampleDataService.findAllOrders()));
		return html(rendered);
	}

	@GetMapping("/demo/macros")
	public ResponseEntity<String> macros() throws IOException, TemplateException {
		String source = """
				<#macro productRow product>
				  <tr><td>${product.name()}</td><td>$${product.price()?string("0.00")}</td></tr>
				</#macro>
				<table>
				<#list products as product>
				  <@productRow product=product/>
				</#list>
				</table>
				""";
		String rendered = render("macros", source, Map.of("products", sampleDataService.findAllProducts()));
		return html(rendered);
	}

	@GetMapping("/demo/functions")
	public ResponseEntity<String> functions() throws IOException, TemplateException {
		String source = """
				<#function discountedPrice price percentOff>
				  <#return price - (price * percentOff / 100)>
				</#function>
				<p>Discounted price: $${discountedPrice(product.price(), 10)?string("0.00")}</p>
				""";
		Product product = new Product("p1", "Widget", new BigDecimal("9.99"), 100);
		String rendered = render("functions", source, Map.of("product", product));
		return html(rendered);
	}

	@GetMapping("/demo/builtins")
	public ResponseEntity<String> builtins() throws IOException, TemplateException {
		String source = """
				<p>Upper: ${name?upper_case}</p>
				<p>Price: $${price?string("0.00")}</p>
				<p>Placed at: ${placedAt?string("yyyy-MM-dd")}</p>
				""";
		Map<String, Object> model = Map.of("name", "widget", "price", new BigDecimal("9.9"), "placedAt",
				Date.from(Instant.parse("2026-07-01T10:15:30Z")));
		String rendered = render("builtins", source, model);
		return html(rendered);
	}

	@GetMapping("/demo/composition")
	public ResponseEntity<String> composition() throws IOException, TemplateException {
		String source = """
				<#import "layout.ftlh" as layout>
				<@layout.page title="Composed Fragment">
				<p>This fragment reuses the same layout.ftlh macro the MVC pages use, via #import.</p>
				</@layout.page>
				""";
		String rendered = render("composition", source, Map.of());
		return html(rendered);
	}

	@GetMapping("/demo/null-safety")
	public ResponseEntity<String> nullSafety() throws IOException, TemplateException {
		String source = """
				<p>Status (default operator): ${order.status()!"pending"}</p>
				<p>Status exists? <#if order.status()??>yes<#else>no</#if></p>
				""";
		Order sparseOrder = sampleDataService.findOrder("o2").orElseThrow();
		String rendered = render("null-safety", source, Map.of("order", sparseOrder));
		return html(rendered);
	}

	private String render(String templateName, String source, Object dataModel) throws IOException, TemplateException {
		Template template = new Template(templateName, new StringReader(source), demoFreemarkerConfiguration);
		StringWriter writer = new StringWriter();
		template.process(dataModel, writer);
		return writer.toString();
	}

	private ResponseEntity<String> html(String body) {
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
	}
}
