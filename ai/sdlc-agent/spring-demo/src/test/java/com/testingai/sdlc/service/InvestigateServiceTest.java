package com.testingai.sdlc.service;

import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigateServiceTest {

    private static final Ticket TICKET = new Ticket("DEMO-101", "title", "description", "High", "checkout-service",
            Instant.parse("2026-07-10T10:00:00Z"));

    @Mock
    private TicketSource ticketSource;
    @Mock
    private InvestigationLoop investigationLoop;

    private InvestigateService investigateService;

    @BeforeEach
    void setUp() {
        investigateService = new InvestigateService(ticketSource, investigationLoop);
    }

    @Test
    void investigate_shouldFetchTicketThenDelegateToInvestigationLoop() {
        when(ticketSource.fetch("DEMO-101")).thenReturn(TICKET);
        InvestigateResponse expected = new InvestigateResponse(
                new RootCauseHypothesis("summary", List.of(), "high", List.of()), 2, List.of(), false);
        when(investigationLoop.investigate(TICKET)).thenReturn(expected);

        InvestigateResponse result = investigateService.investigate("DEMO-101");

        assertThat(result).isEqualTo(expected);
        verify(ticketSource).fetch("DEMO-101");
        verify(investigationLoop).investigate(TICKET);
    }
}
