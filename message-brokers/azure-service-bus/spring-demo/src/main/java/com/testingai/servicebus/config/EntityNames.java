package com.testingai.servicebus.config;

public class EntityNames {

	private EntityNames() {
	}

	public static final String SIMPLE_QUEUE = "simple-queue";
	public static final String WORK_QUEUE = "work-queue";
	public static final String DLQ_QUEUE = "dlq-queue";
	public static final String SESSION_QUEUE = "session-queue";
	public static final String TX_QUEUE = "tx-queue";

	public static final String PUBSUB_TOPIC = "pubsub-topic";
	public static final String ROUTING_TOPIC = "routing-topic";

	public static final String PUBSUB_SUB_A = "sub-a";
	public static final String PUBSUB_SUB_B = "sub-b";
	public static final String ROUTING_SUB_ALL = "sub-all";
	public static final String ROUTING_SUB_ERROR = "sub-error";

	public static final String ROUTING_KEY = "level";
}
