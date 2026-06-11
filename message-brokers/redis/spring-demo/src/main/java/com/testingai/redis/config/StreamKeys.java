package com.testingai.redis.config;

public class StreamKeys {

    private StreamKeys() {}

    public static final String SIMPLE  = "{streams}:simple";
    public static final String WORK    = "{streams}:work";
    public static final String FANOUT  = "{streams}:fanout";
    public static final String PENDING = "{streams}:pending";
    public static final String TRIMMED = "{streams}:trimmed";

    public static final String PUBSUB_CHANNEL = "demo:pubsub";
    public static final int    TRIM_MAX_LEN   = 100;
}
