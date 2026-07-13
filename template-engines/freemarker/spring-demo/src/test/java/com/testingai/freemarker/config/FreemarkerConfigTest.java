package com.testingai.freemarker.config;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreemarkerConfigTest {

	private final freemarker.template.Configuration configuration = new FreemarkerConfig()
			.demoFreemarkerConfiguration();

	@Test
	void configuration_shouldRethrowTemplateExceptionsInsteadOfEmbeddingHtmlDebugOutput() {
		assertThatThrownBy(() -> {
			Template template = new Template("broken", new StringReader("${missing.field}"), configuration);
			template.process(Map.of(), new StringWriter());
		}).isInstanceOf(TemplateException.class);
	}
}
