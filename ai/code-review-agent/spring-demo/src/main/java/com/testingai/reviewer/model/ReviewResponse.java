package com.testingai.reviewer.model;

import java.util.List;

public record ReviewResponse(List<Finding> findings, String summary) {}
