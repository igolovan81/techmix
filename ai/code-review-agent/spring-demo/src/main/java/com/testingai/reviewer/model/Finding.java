package com.testingai.reviewer.model;

public record Finding(String severity, String file, int line, String message, String suggestion) {}
