package com.testingai.sqs.util;

import java.util.Random;

public class FailureSimulator {

    private static final Random RANDOM = new Random();

    private FailureSimulator() {}

    /** Returns true ~50 % of the time — used by DLQ demo to simulate processing failures. */
    public static boolean shouldFail() {
        return RANDOM.nextInt(100) < 50;
    }
}
