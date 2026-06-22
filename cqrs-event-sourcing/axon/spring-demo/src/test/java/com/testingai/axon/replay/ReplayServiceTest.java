package com.testingai.axon.replay;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

	@InjectMocks
	private ReplayService replayService;

	@Mock
	private EventProcessingConfiguration eventProcessingConfiguration;

	@Mock
	private TrackingEventProcessor trackingEventProcessor;

	@Test
	void replayOrderProjection_shouldShutDownResetAndRestartTheProcessor() {
		when(eventProcessingConfiguration.eventProcessor("order-projection", TrackingEventProcessor.class))
				.thenReturn(Optional.of(trackingEventProcessor));

		replayService.replayOrderProjection();

		verify(trackingEventProcessor).shutDown();
		verify(trackingEventProcessor).resetTokens();
		verify(trackingEventProcessor).start();
	}
}
