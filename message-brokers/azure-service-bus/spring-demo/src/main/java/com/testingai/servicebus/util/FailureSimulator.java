package com.testingai.servicebus.util;

public class FailureSimulator {

    private FailureSimulator() {}

    public static boolean shouldFail() {
        return Math.random() < 0.50;
    }
}
