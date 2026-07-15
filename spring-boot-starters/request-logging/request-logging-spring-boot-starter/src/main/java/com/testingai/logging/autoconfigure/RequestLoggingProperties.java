package com.testingai.logging.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("app.logging.request")
public record RequestLoggingProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("false") boolean includeBody, @DefaultValue("/actuator/**") List<String> excludedPaths) {
}
