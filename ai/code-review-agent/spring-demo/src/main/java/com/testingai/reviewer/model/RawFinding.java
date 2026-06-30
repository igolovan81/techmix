package com.testingai.reviewer.model;

public record RawFinding(String file, String tool, String rule, String message, int line) {}
