package com.testingai.reviewer.model;

import java.util.Map;
import java.util.Set;

public record ParsedDiff(
        Map<String, String> fileContents,
        Map<String, Set<Integer>> changedLines
) {}
