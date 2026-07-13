package com.testingai.handlebars.controller;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.Product;
import com.testingai.handlebars.service.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DemoController {

	private static final int PRECOMPILE_COMPARISON_ITERATIONS = 200;

	private final Handlebars handlebars;
	private final SampleDataService sampleDataService;

	@GetMapping("/demo/variables")
	public ResponseEntity<String> variables() throws IOException {
		Template template = handlebars.compileInline("<p>Escaped: {{name}}</p><p>Raw: {{{rawHtml}}}</p>");
		String rendered = template.apply(Map.of("name", "<b>Widget</b>", "rawHtml", "<b>Widget</b>"));
		return html(rendered);
	}

	@GetMapping("/demo/helpers/builtin")
	public ResponseEntity<String> builtinHelpers() throws IOException {
		String source = """
				<ul>
				{{#each products}}
				  <li>{{#if stock}}{{name}} - in stock{{else}}{{name}} - out of stock{{/if}}</li>
				{{/each}}
				</ul>
				{{#with customerOrder}}
				<p>Order for {{customer}}</p>
				{{/with}}
				""";
		Template template = handlebars.compileInline(source);
		Map<String, Object> context = Map.of("products", sampleDataService.findAllProducts(), "customerOrder",
				sampleDataService.findOrder("o1").orElseThrow());
		return html(template.apply(context));
	}

	@GetMapping("/demo/helpers/custom")
	public ResponseEntity<String> customHelper() throws IOException {
		String source = """
				<ul>
				{{#each products}}
				  <li>{{name}}: {{formatCurrency price}}</li>
				{{/each}}
				</ul>
				""";
		Template template = handlebars.compileInline(source);
		return html(template.apply(Map.of("products", sampleDataService.findAllProducts())));
	}

	@GetMapping("/demo/partials")
	public ResponseEntity<String> partials() throws IOException {
		Template template = handlebars.compile("partials/order-item");
		Order order = sampleDataService.findOrder("o1").orElseThrow();
		return html(template.apply(order.items().getFirst()));
	}

	@GetMapping("/demo/layout")
	public ResponseEntity<String> layout() throws IOException {
		String childSource = """
				{{#partial "body"}}
				<p>Custom body content injected into the shared layout.</p>
				{{/partial}}
				{{> layout}}
				""";
		Template template = handlebars.compileInline(childSource);
		return html(template.apply(null));
	}

	@GetMapping("/demo/subexpressions")
	public ResponseEntity<String> subexpressions() throws IOException {
		Template template = handlebars.compileInline("<p>Line total: {{formatCurrency (multiply price quantity)}}</p>");
		String rendered = template.apply(Map.of("price", new BigDecimal("9.99"), "quantity", new BigDecimal("3")));
		return html(rendered);
	}

	@GetMapping("/demo/precompiled")
	public ResponseEntity<String> precompiled() throws IOException {
		String source = "{{#each products}}{{name}}: {{formatCurrency price}} | {{/each}}";
		List<Product> products = sampleDataService.findAllProducts();
		Map<String, Object> context = Map.of("products", products);

		long compileEachTimeStart = System.nanoTime();
		for (int i = 0; i < PRECOMPILE_COMPARISON_ITERATIONS; i++) {
			handlebars.compileInline(source).apply(context);
		}
		long compileEachTimeNanos = System.nanoTime() - compileEachTimeStart;

		Template precompiledTemplate = handlebars.compileInline(source);
		long reuseStart = System.nanoTime();
		for (int i = 0; i < PRECOMPILE_COMPARISON_ITERATIONS; i++) {
			precompiledTemplate.apply(context);
		}
		long reuseNanos = System.nanoTime() - reuseStart;

		String body = String.format("<p>Compiled 200 times: %d ms</p><p>Precompiled, applied 200 times: %d ms</p>",
				compileEachTimeNanos / 1_000_000, reuseNanos / 1_000_000);
		return html(body);
	}

	private ResponseEntity<String> html(String body) {
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
	}
}
