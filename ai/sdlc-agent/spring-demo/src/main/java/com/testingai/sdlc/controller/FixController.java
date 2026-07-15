package com.testingai.sdlc.controller;

import com.testingai.sdlc.model.FixRequest;
import com.testingai.sdlc.model.FixResponse;
import com.testingai.sdlc.service.FixService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sdlc")
public class FixController {

    private final FixService fixService;

    public FixController(FixService fixService) {
        this.fixService = fixService;
    }

    @PostMapping("/fix")
    public FixResponse fix(@RequestBody @Valid FixRequest request) {
        return fixService.fix(request.ticketId());
    }
}
