package com.testingai.freemarker.config;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class FreemarkerConfig {

	@Bean
	public freemarker.template.Configuration demoFreemarkerConfiguration() {
		freemarker.template.Configuration configuration = new freemarker.template.Configuration(
				freemarker.template.Configuration.VERSION_2_3_31);
		configuration.setDefaultEncoding("UTF-8");
		configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		configuration.setLogTemplateExceptions(false);
		configuration.setTimeZone(TimeZone.getTimeZone("UTC"));
		configuration.setTemplateLoader(new ClassTemplateLoader(FreemarkerConfig.class, "/templates"));
		return configuration;
	}
}
