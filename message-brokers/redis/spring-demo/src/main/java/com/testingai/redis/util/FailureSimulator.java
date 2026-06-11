package com.testingai.redis.util;

import java.util.random.RandomGenerator;

public class FailureSimulator {

    private static final double FAILURE_RATE = 0.05;

    private FailureSimulator() {}

    public static void maybeThrow(String context) {
        if (RandomGenerator.getDefault().nextDouble() < FAILURE_RATE) {
            throw new RuntimeException("Simulated failure in " + context);
        }
    }
}
