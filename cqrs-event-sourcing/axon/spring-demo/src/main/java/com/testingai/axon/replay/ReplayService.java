package com.testingai.axon.replay;

import lombok.RequiredArgsConstructor;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplayService {

	private static final String PROCESSING_GROUP = "order-projection";

	private final EventProcessingConfiguration eventProcessingConfiguration;

	public void replayOrderProjection() {
		eventProcessingConfiguration.eventProcessor(PROCESSING_GROUP, TrackingEventProcessor.class)
				.ifPresent(processor -> {
					processor.shutDown();
					processor.resetTokens();
					processor.start();
				});
	}
}
