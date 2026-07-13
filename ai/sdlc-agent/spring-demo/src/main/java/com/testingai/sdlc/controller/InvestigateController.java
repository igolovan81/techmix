package com.testingai.sdlc.controller;

import com.testingai.sdlc.model.InvestigateRequest;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.service.InvestigateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sdlc")
public class InvestigateController {

    private final InvestigateService investigateService;

    public InvestigateController(InvestigateService investigateService) {
        this.investigateService = investigateService;
    }

    @PostMapping("/investigate")
    public InvestigateResponse investigate(@RequestBody @Valid InvestigateRequest request) {
        return investigateService.investigate(request.ticketId());
    }
}
