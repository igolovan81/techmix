package com.testingai.handlebars.config;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HandlebarsConfigTest {

	private final Handlebars handlebars = new HandlebarsConfig().handlebars();

	@Test
	void formatCurrencyHelper_shouldFormatBigDecimalAsTwoDecimalDollarAmount() throws IOException {
		Template template = handlebars.compileInline("{{formatCurrency price}}");

		String result = template.apply(new PriceHolder(new BigDecimal("9.5")));

		assertThat(result).isEqualTo("$9.50");
	}

	@Test
	void formatCurrencyHelper_shouldAcceptStringInputFromASubexpression() throws IOException {
		Template template = handlebars.compileInline("{{formatCurrency \"12.3\"}}");

		String result = template.apply(null);

		assertThat(result).isEqualTo("$12.30");
	}

	@Test
	void multiplyHelper_shouldMultiplyValueByParam() throws IOException {
		Template template = handlebars.compileInline("{{multiply price 3}}");

		String result = template.apply(new PriceHolder(new BigDecimal("2.00")));

		assertThat(new BigDecimal(result)).isEqualByComparingTo(new BigDecimal("6.00"));
	}

	public record PriceHolder(BigDecimal price) {
	}
}
