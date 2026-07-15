package com.testingai.sdlc.service;

import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import org.springframework.stereotype.Service;

@Service
public class InvestigateService {

    private final TicketSource ticketSource;
    private final InvestigationLoop investigationLoop;

    public InvestigateService(TicketSource ticketSource, InvestigationLoop investigationLoop) {
        this.ticketSource = ticketSource;
        this.investigationLoop = investigationLoop;
    }

    public InvestigateResponse investigate(String ticketId) {
        Ticket ticket = ticketSource.fetch(ticketId);
        return investigationLoop.investigate(ticket);
    }
}
