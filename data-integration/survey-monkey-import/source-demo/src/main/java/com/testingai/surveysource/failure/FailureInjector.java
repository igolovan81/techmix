package com.testingai.surveysource.failure;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.domain.FailureMode;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class FailureInjector {

	private final AtomicReference<FailureConfig> config = new AtomicReference<>(
			new FailureConfig(FailureMode.NONE, 0.0));

	public void configure(FailureConfig newConfig) {
		config.set(newConfig);
	}

	public FailureConfig current() {
		return config.get();
	}

	public boolean shouldInject(FailureMode mode) {
		FailureConfig active = config.get();
		return active.mode() == mode && Math.random() < active.rate();
	}
}
