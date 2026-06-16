package com.testingai.sqs.config;

public class QueueNames {

	private QueueNames() {
	}

	public static final String SIMPLE = "simple-queue";
	public static final String WORK = "work-queue";
	public static final String FANOUT_A = "fanout-queue-a";
	public static final String FANOUT_B = "fanout-queue-b";
	public static final String RETRY = "retry-queue";
	public static final String RETRY_DLQ = "retry-dlq";
	public static final String FIFO = "fifo-queue.fifo";
	public static final String PUBSUB_A = "pubsub-queue-a";
	public static final String PUBSUB_B = "pubsub-queue-b";

	public static final String FANOUT_TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:fanout-topic";
	public static final String PUBSUB_TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:pubsub-topic";
}
