package com.testingai.reviewer.controller;

import com.testingai.reviewer.model.ReviewRequest;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/analyse")
    public ReviewResponse analyse(@RequestBody @Valid ReviewRequest request) {
        return reviewService.analyse(request.diff());
    }
}
