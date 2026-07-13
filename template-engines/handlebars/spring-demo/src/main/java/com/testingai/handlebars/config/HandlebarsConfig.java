package com.testingai.handlebars.config;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.springmvc.HandlebarsViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class HandlebarsConfig {

	public static final String TEMPLATE_PREFIX = "/templates";
	public static final String TEMPLATE_SUFFIX = ".hbs";
	public static final String FORMAT_CURRENCY_HELPER = "formatCurrency";
	public static final String MULTIPLY_HELPER = "multiply";

	@Bean
	public Handlebars handlebars() {
		Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader(TEMPLATE_PREFIX, TEMPLATE_SUFFIX));
		handlebars.registerHelper(FORMAT_CURRENCY_HELPER, formatCurrencyHelper());
		handlebars.registerHelper(MULTIPLY_HELPER, multiplyHelper());
		return handlebars;
	}

	@Bean
	public HandlebarsViewResolver handlebarsViewResolver() {
		HandlebarsViewResolver resolver = new HandlebarsViewResolver();
		resolver.setPrefix("classpath:" + TEMPLATE_PREFIX + "/");
		resolver.setSuffix(TEMPLATE_SUFFIX);
		resolver.registerHelper(FORMAT_CURRENCY_HELPER, formatCurrencyHelper());
		resolver.registerHelper(MULTIPLY_HELPER, multiplyHelper());
		return resolver;
	}

	private Helper<Object> formatCurrencyHelper() {
		return (value, options) -> {
			if (value == null) {
				return "$0.00";
			}
			BigDecimal amount = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
			return String.format("$%.2f", amount);
		};
	}

	private Helper<Number> multiplyHelper() {
		return (value, options) -> {
			Number factor = options.param(0);
			return toBigDecimal(value).multiply(toBigDecimal(factor)).toString();
		};
	}

	private static BigDecimal toBigDecimal(Number number) {
		return number instanceof BigDecimal bd ? bd : BigDecimal.valueOf(number.doubleValue());
	}
}
